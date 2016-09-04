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
 * Removes restrictions imposed by holy altars (e.g. white light, black
 * light) to terraform, build structures, and found settlements, as well
 * as founding settlements near aggressive creatures and dens.
 */
public class NoHolyGround implements WurmServerMod, PreInitable {
    @Override
    public void preInit() {
        RemoveBlockingRestrictions();
    }    
    

    private void RemoveBlockingRestrictions() {
        try {
            // Disables founding settlements near altars, aggressive creatures and dens.
            CtClass ctClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.questions.VillageFoundationQuestion");
            CtClass[] parameters = new CtClass[0];
            CtMethod method = ctClass.getMethod("answersFail", Descriptor.ofMethod(CtPrimitiveType.booleanType, parameters));
            method.instrument(new ExprEditor() { 
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    String methodName = methodCall.getMethodName();
                    
                    /**
                     * Replaces the original code in "answersFail",
                     * if (!this.checkBlockingCreatures()) return true;
                     * if (!this.checkBlockingItems()) return true;
                     * 
                     * with
                     * 
                     * if (!true) return true;
                     * 
                     * effectively always allowing founding a settlement regardless
                     * of altars and aggressive creatures or dens.
                     */
                    if (methodName.equals("checkBlockingCreatures"))
                        methodCall.replace("$_ = true;");
                    else if (methodName.equals("checkBlockingItems"))
                        methodCall.replace("$_ = true;");
                }
            } );
            
            // Disables restrictions on terraforming near altars.
            ctClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.Terraforming");
            parameters = new CtClass[] {
                HookManager.getInstance().getClassPool().get("com.wurmonline.server.creatures.Creature"),
                CtPrimitiveType.intType,
                CtPrimitiveType.intType };
            method = ctClass.getMethod("isAltarBlocking", Descriptor.ofMethod(CtPrimitiveType.booleanType, parameters));
            method.instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    String methodName = methodCall.getMethodName();
                    
                    /**
                     * Replaces the original code,
                     * 
                     * EndGameItem alt = EndGameItems.getEvilAltar();
                     * if (alt != null) {
                     * 
                     * with
                     * 
                     * EndGameItem alt = null;
                     * 
                     * This will effectively disable the whole block of code.
                     */
                    if (methodName.equals("getEvilAltar"))
                        methodCall.replace("$_ = null;");
                    else if (methodName.equals("getGoodAltar"))
                        methodCall.replace("$_ = null;");
                }
            } );
            
            // Remove altar blocking from creating structures.
            ctClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.MethodsStructure");
            parameters = new CtClass[] {
                HookManager.getInstance().getClassPool().get("com.wurmonline.server.creatures.Creature"),
                HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item"),
                CtPrimitiveType.intType,
                CtPrimitiveType.intType,
                CtPrimitiveType.intType };
            method = ctClass.getMethod("canPlanStructureAt", Descriptor.ofMethod(CtPrimitiveType.booleanType, parameters));
            method.instrument(new ExprEditor() { 
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    String methodName = methodCall.getMethodName();
                    
                    if (methodName.equals("getEvilAltar"))
                        methodCall.replace("$_ = null;");
                    else if (methodName.equals("getGoodAltar"))
                        methodCall.replace("$_ = null;");
                }
            });
        } catch (NotFoundException | CannotCompileException ex) {
            Logger.getLogger(NoHolyGround.class.getName()).log(Level.SEVERE, null, ex);
        }        
    }
}
