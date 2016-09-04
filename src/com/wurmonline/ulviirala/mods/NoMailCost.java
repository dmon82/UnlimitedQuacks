package com.wurmonline.ulviirala.mods;

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
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;
/**
 *
 * Removes the postage from sending items via mail, as well as the postage from returned/rejected mail.
 */
public class NoMailCost implements WurmServerMod, PreInitable {
    @Override
    public void preInit() {
        try {
            CtClass ctClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.questions.MailSendConfirmQuestion");
            CtClass[] parameters = new CtClass[] { HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item"), CtPrimitiveType.floatType };
            CtMethod method = ctClass.getMethod("getCostForItem", Descriptor.ofMethod(CtPrimitiveType.intType, parameters));
            method.setBody("{ return 0; }");
            // could set "charge" to false in answer method instead, so server doesn't warn about charging 0 money, and mail was free.
            ctClass.toClass();
            
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
            
            // Not really necessary, but this method displays info about the cost in the UI,
            // which is identical to the method above.
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
}
