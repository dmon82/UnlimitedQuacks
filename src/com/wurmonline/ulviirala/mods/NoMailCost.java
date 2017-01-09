package com.wurmonline.ulviirala.mods;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtPrimitiveType;
import javassist.NotFoundException;
import javassist.bytecode.Descriptor;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import javassist.expr.NewExpr;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;
/**
 *
 * Disables postage cost and places Courier enchants on newly created mailboxes.
 */
public class NoMailCost implements WurmServerMod, PreInitable, Configurable {
    private boolean _DisablePostage = true;
    private boolean _EnableEnchant = true;
    private float _EnchantPower = 30f;
    
    @Override
    public void preInit() {
        if (_DisablePostage)
            DisablePostage();
        
        if (_EnableEnchant)
            EnableEnchant();
    }

    /**
     * Enables Courier enchants of newly created mailboxes.
     */
    private void EnableEnchant() {
        try {
            // Places a 30 power courier enchantment on newly created mailboxes.
            CtClass ctClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.ItemFactory");
            CtClass[] parameters = new CtClass[] {
                CtPrimitiveType.intType,
                CtPrimitiveType.floatType,
                CtPrimitiveType.byteType,
                CtPrimitiveType.byteType,
                CtPrimitiveType.longType,
                HookManager.getInstance().getClassPool().get("java.lang.String")
            };
            CtMethod ctMethod = ctClass.getMethod("createItem", Descriptor.ofMethod(HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item"), parameters));
            ctMethod.instrument(new ExprEditor() { 
                @Override
                public void edit(NewExpr newExpr) throws CannotCompileException {
                    if (newExpr.getClassName().equals("com.wurmonline.server.items.DbItem")) {
                        ctMethod.insertAt(newExpr.getLineNumber() + 1, 
                                "{ if (templateId >= 510 && templateId <= 513) {" +
                                        "com.wurmonline.server.items.ItemSpellEffects effs;" +
                                        "if ((effs = toReturn.getSpellEffects()) == null) effs = new com.wurmonline.server.items.ItemSpellEffects(toReturn.getWurmId());" +
                                        "toReturn.getSpellEffects().addSpellEffect(new com.wurmonline.server.spells.SpellEffect(toReturn.getWurmId(), (byte)20, " + _EnchantPower + "f, 20000000));" +
                                        "toReturn.permissions.setPermissionBit(com.wurmonline.server.players.Permissions.Allow.HAS_COURIER.getBit(), true);"+
                                        "} }");
                    }
                }
            });
        } catch (NotFoundException | CannotCompileException ex) {
            Logger.getLogger(NoMailCost.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * Disables postage cost for sending mail and receiving returned mails.
     */
    private void DisablePostage() {
        try {
            CtClass ctClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.questions.MailSendConfirmQuestion");
            CtClass[] parameters = new CtClass[] { HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item"), CtPrimitiveType.floatType };
            CtMethod method = ctClass.getMethod("getCostForItem", Descriptor.ofMethod(CtPrimitiveType.intType, parameters));
            method.setBody("{ return 0; }");
            // could set "charge" to false in answer method instead, so server doesn't warn about charging 0 money, and mail was free.
            
            
            
            ctClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.questions.MailReceiveQuestion");
            parameters = new CtClass[] { HookManager.getInstance().getClassPool().get("java.util.Properties") };
            // Can't re-use previous variable, they must be final
            // or "effectively" final, to be called from within the
            // ExprEditor.
            CtMethod retrieveAnswer = ctClass.getMethod("answer", Descriptor.ofMethod(CtPrimitiveType.voidType, parameters));
            retrieveAnswer.instrument(new ExprEditor() { 
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    // voids all cost, including Cash On Delivery.
                    /*if (methodCall.getMethodName().equals("getMoney"))
                        ctMethod.insertAt(methodCall.getLineNumber(), "{ fullcost = 0; }");*/
                    
                    // after assigning "pcost = 100"; this could effectively assign "pcost = 0",
                    // resetting the default mail cost for returned/rejected mail to 0 instead of 100i.
                    if (methodCall.getMethodName().equals("getTemplateId"))
                        retrieveAnswer.insertAt(methodCall.getLineNumber(), "{ pcost = 0; }");
                }
            });
            
            // Not really necessary, but this method displays info about the cost in the UI.
            parameters = new CtClass[] {
                HookManager.getInstance().getClassPool().get("java.lang.String"),
                HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item"),
                HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.WurmMail"),
                CtPrimitiveType.booleanType
            };
            CtMethod retrieveQuestion = ctClass.getMethod("addItem", Descriptor.ofMethod(HookManager.getInstance().getClassPool().get("java.lang.String"), parameters));
            retrieveQuestion.instrument(new ExprEditor() { 
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    if (methodCall.getMethodName().equals("getTemplateId"))
                        retrieveQuestion.insertAt(methodCall.getLineNumber(), "{ pcost = 0; }");
                }
            });
        } catch (NotFoundException | CannotCompileException ex) {
            Logger.getLogger(NoMailCost.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    @Override
    public void configure(Properties properties) {
        _DisablePostage = Boolean.valueOf(properties.getProperty("disablePostage", String.valueOf(_DisablePostage)));
        Logger.getLogger(NoMailCost.class.getName()).log(Level.INFO, String.format("Mod will%s disable mail postage cost.", (!_DisablePostage ? " not" : "")));
        
        _EnableEnchant = Boolean.valueOf(properties.getProperty("enableEnchant", String.valueOf(_EnableEnchant)));
        _EnchantPower = Float.valueOf(properties.getProperty("enchantPower", String.valueOf(_EnchantPower)));
        
        if (_EnchantPower < 1.0f || _EnchantPower > 101.0f)
            _EnchantPower = 30.0f;
        
        Logger.getLogger(NoMailCost.class.getName()).log(Level.INFO, String.format("Newly created mailboxes will%s get %f power Courier enchants.", (!_EnableEnchant ? " not": ""), _EnchantPower));
    }
}
