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
 * Displays an estimate of the remaining burn time of a fire before it drops
 * down to a certain temperature.
 */
public class FireBurnTime implements WurmServerMod, PreInitable, Configurable {
    private static final Logger _Logger = Logger.getLogger(FireBurnTime.class.getName());
    
    /**
     * Target temperature.
     * 
        # 0    -  999   The fire is not lit.
        # 1000 - 1999	A few red glowing coals can be found under a bed of ahshes.
        # 2000 - 3499	A layer of ashes is starting to form on the glowing coals.
        # 3500 - 3999	A hot red glowing bed of coal remains of the fire now.
        # 4000 - 4999	A few flames still dance on the fire but soon they too will die.
        # 5000 - 6999	The fire is starting to fade.
        # 7000 - 8999	The fire burns with wild flames and still has much unburnt material.
        # 9000+			The fire burns steadily and will still burn for a long time.
     */
    public int _TargetTemperature = 5000;
    
    @Override
    public void preInit() {
        _Logger.info("Initialising FireBurnTime 1.4.1");
        
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
                                
                            "if (" + _TargetTemperature + " > 8999) performer.getCommunicator().sendNormalServerMessage(\"It will burn steadily for about \" + secondsRemaining / 60 + \" minutes and \" + secondsRemaining % 60 + \" seconds.\");"+
                            "else if (" + _TargetTemperature + " > 6999) performer.getCommunicator().sendNormalServerMessage(\"It will have much unburnt material for about \" + secondsRemaining / 60 + \" minutes and \" + secondsRemaining % 60 + \" seconds.\");"+
                            "else if (" + _TargetTemperature + " > 4999) performer.getCommunicator().sendNormalServerMessage(\"It will not start to fade for about \" + secondsRemaining / 60 + \" minutes and \" + secondsRemaining % 60 + \" seconds.\");"+
                            "else if (" + _TargetTemperature + " > 3999) performer.getCommunicator().sendNormalServerMessage(\"It will have a few dancing flames in about \" + secondsRemaining / 60 + \" minutes and \" + secondsRemaining % 60 + \" seconds.\");"+
                            "else if (" + _TargetTemperature + " > 1999) performer.getCommunicator().sendNormalServerMessage(\"It will have a bed of red glowing coals in about \" + secondsRemaining / 60 + \" minutes and \" + secondsRemaining % 60 + \" seconds.\");"+
                            "else if (" + _TargetTemperature + " > 999) performer.getCommunicator().sendNormalServerMessage(\"It will be a layer of ash in about \" + secondsRemaining / 60 + \" minutes and \" + secondsRemaining % 60 + \" seconds.\");"+
                            "else performer.getCommunicator().sendNormalServerMessage(\"It will be completely cold in about \" + secondsRemaining / 60 + \" minutes and \" + secondsRemaining % 60 + \" seconds.\");"+
                        "}"
                        );
                    }
                }
            });
        } catch (CannotCompileException | NotFoundException ex) {
            _Logger.severe("FireBurnTime 1.4.1 could not be applied.");
            _Logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void configure(Properties properties) {
        _TargetTemperature = Math.max(200, Math.min(9000, Integer.valueOf(properties.getProperty("targetTemperature", String.valueOf(_TargetTemperature)))));
        _Logger.log(Level.INFO, String.format("Burn time estimation will be for %d temperature.", _TargetTemperature));
    }
}
