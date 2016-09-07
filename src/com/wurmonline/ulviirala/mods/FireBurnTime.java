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
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

/**
 *
 * Displays an estimate of the remaining burn time of a fire before only a bed of coal remains.
 */
public class FireBurnTime implements WurmServerMod, PreInitable, Configurable {
    public int _TargetTemperature = 5000;
    
    @Override
    public void preInit() {
        try {
            CtClass ctClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.FireBehaviour");
            CtClass[] parameters = new CtClass[] {
                HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.Action"),
                HookManager.getInstance().getClassPool().get("com.wurmonline.server.creatures.Creature"),
                HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item"),
                CtPrimitiveType.shortType,
                CtPrimitiveType.floatType
            };
            CtMethod ctMethod = ctClass.getMethod("action", Descriptor.ofMethod(CtPrimitiveType.booleanType, parameters));
            ctMethod.instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    if (methodCall.getMethodName().equals("sendEnchantmentStrings")) {
                        ctMethod.insertAt(methodCall.getLineNumber(), 
                        "{" +
                            "float coolingSpeed = 1.0f;"+
                            "com.wurmonline.server.zones.VolaTile forgeTile = com.wurmonline.server.zones.Zones.getTileOrNull(target.getTilePos(), target.isOnSurface());"+
                            "if (forgeTile != null && forgeTile.getStructure() != null)"+
                            "    coolingSpeed *= 0.75f;"+
                            "else if (com.wurmonline.server.Server.getWeather().getRain() > 0.2f)"+
                            "    coolingSpeed *= 2f;"+

                            "if (target.getRarity() > 0)"+
                            "    coolingSpeed *= Math.pow(0.8999999761581421, (double)target.getRarity());"+

                            "float decreaseTemperature = coolingSpeed * Math.max(1f, 11f - Math.max(1f, 20f * Math.max(30f, target.getCurrentQualityLevel()) / 200f));"+
                            "int secondsRemaining = Math.round(Math.max(0, target.getTemperature() - " + _TargetTemperature + ") / decreaseTemperature);"+
                            "String remaining = \"It will be pretty hot for about \" + secondsRemaining / 60 + \" minutes and \" + secondsRemaining % 60 + \" seconds.\";"+

                            "performer.getCommunicator().sendNormalServerMessage(remaining);"+
                        "}"
                        );
                    }
                }
            });
        } catch (CannotCompileException | NotFoundException ex) {
            Logger.getLogger(FireBurnTime.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void configure(Properties properties) {
        _TargetTemperature = Math.max(4000, Math.min(9000, Integer.valueOf(properties.getProperty("targetTemperature", String.valueOf(_TargetTemperature)))));
        Logger.getLogger(FireBurnTime.class.getName()).log(Level.INFO, String.format("Burn time estimation will be for %d temperature.", _TargetTemperature));
    }
}
