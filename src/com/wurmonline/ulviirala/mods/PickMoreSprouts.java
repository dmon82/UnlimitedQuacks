package com.wurmonline.ulviirala.mods;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

/**
 *
 * Allows you to pick more sprouts from one tree/one action.
 */
public class PickMoreSprouts implements WurmServerMod, PreInitable, Configurable {
    private int _Count = 5;
    
    @Override
    public void preInit() {
        try {
            CtClass ctClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.Terraforming");
            CtMethod ctMethod = ctClass.getMethod("pickSprout", "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIILcom/wurmonline/mesh/Tiles$Tile;FLcom/wurmonline/server/behaviours/Action;)Z");
            ctMethod.instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    if (methodCall.getMethodName().equals("insertItem")) {
                        ctMethod.insertAt(methodCall.getLineNumber() + 1, 
                                "{ for (int iSprout = 0; iSprout <" + (_Count - 1) + "; iSprout++) {"+
                                        "sprout = com.wurmonline.server.items.ItemFactory.createItem(266, Math.max(1.0f, (float)power), material, act.getRarity(), null);" +
                                        "if (power < 0.0) { sprout.setDamage((float)(-power)/2.0f); }"+
                                        "sprout.setData1(data1);"+
                                        "performer.getInventory().insertItem(sprout); }"+
                                " }"
                        );
                    }
                }
            });
        } catch (NotFoundException | CannotCompileException ex) {
            Logger.getLogger(PickMoreSprouts.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void configure(Properties properties) {
        _Count = Math.max(1, Math.min(64, Integer.valueOf(properties.getProperty("count", String.valueOf(_Count)))));
        Logger.getLogger(PickMoreSprouts.class.getName()).log(Level.INFO, String.format("You will pick %d sprouts each now.", _Count));
    }
}
