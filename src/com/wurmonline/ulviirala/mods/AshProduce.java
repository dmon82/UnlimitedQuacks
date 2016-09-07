package com.wurmonline.ulviirala.mods;

import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemFactory;
import java.io.IOException;
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
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

/**
 *
 * Makes forges, ovens, campfires, smelters, and kilns produce ash while burning
 * with a 1 in N chance per second.
 */
public class AshProduce implements WurmServerMod, PreInitable, Configurable {
    private int _AshChance = 60;
    private int _AshStacks = 1;
    
    @Override
    public void preInit() {
        try {
            CtClass ctClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item");
            CtClass[] parameters = new CtClass[] { CtPrimitiveType.booleanType, CtPrimitiveType.booleanType };
            CtMethod ctMethod = ctClass.getMethod("coolOutSideItem", Descriptor.ofMethod(CtPrimitiveType.voidType, parameters));
            ctMethod.instrument(new ExprEditor() { 
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    /*if (methodCall.getMethodName().equals("getTemplateId")) {
                        ctMethod.insertAt(methodCall.getLineNumber() + 1, 
                                "{ this.insertItem(com.wurmonline.server.items.ItemFactory.createItem(141, this.getQualityLevel(), null), true); }");
                    }*/ 
                    if (methodCall.getMethodName().equals("getTemplateId")) {
                        ctMethod.insertAt(methodCall.getLineNumber(), 
                        "{ if (new java.util.Random().nextInt(" + String.valueOf(_AshChance) + ") == 0) {" +
                             "com.wurmonline.server.items.Item[] forgeItems = this.getItemsAsArray();"+
                             "boolean combinedAsh = false;"+
                             "int ashStacks = 0;"+
                             "for (int iForge = 0; iForge < forgeItems.length; iForge++) {"+
                             "     if (forgeItems[iForge].getTemplateId() == 141) {"+
                             "          ashStacks++;" +
                             "          if (forgeItems[iForge].getWeightGrams() < 6400" + (_AshStacks > 0 ? " && ashStacks <= " + _AshStacks : "") + ") {"+
                             "              forgeItems[iForge].setWeight(forgeItems[iForge].getWeightGrams() + 100, true);"+
                             "              combinedAsh = true;"+
                             "              break;"+
                             "          }"+
                             "     }"+
                             "}"+

                             "if (!combinedAsh" + (_AshStacks > 0 ? " && ashStacks < " + _AshStacks : "") + ") {"+
                             "     this.insertItem(com.wurmonline.server.items.ItemFactory.createItem(141, this.getCurrentQualityLevel(), null), true);"+
                             "}"+
                        "} }");
                    }
                }
            });
        } catch (CannotCompileException | NotFoundException ex) {
            Logger.getLogger(AshProduce.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void configure(Properties properties) {
        _AshChance = Math.max(1, Integer.valueOf(properties.getProperty("ashChance", String.valueOf(_AshChance))));
        _AshStacks = Math.max(0, Math.min(100, Integer.valueOf(properties.getProperty("ashStacks", String.valueOf(_AshStacks)))));
        
        Logger.getLogger(AshProduce.class.getName()).log(Level.INFO, String.format("Fireplaces will now produce ash with a 1 in %d chance per second.", _AshChance));
        Logger.getLogger(AshProduce.class.getName()).log(Level.INFO, String.format("Fireplaces will produce %s stacks of ash.", (_AshStacks == 0 ? "unlimited" : String.valueOf(_AshStacks))));
    }
}
