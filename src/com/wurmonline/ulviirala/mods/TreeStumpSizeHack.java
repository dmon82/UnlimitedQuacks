package com.wurmonline.ulviirala.mods;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtPrimitiveType;
import javassist.NotFoundException;
import javassist.bytecode.Descriptor;
import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

/**
 *
 * Item models can scale by their weight, this is done
 * in com.wurmonline.server.items.Item.getSizeMod()F. The templateId for
 * a tree stump is 731, here we simply return 10.0f as a temporary measure.
 */
public class TreeStumpSizeHack implements WurmServerMod, PreInitable {

    @Override
    public void preInit() {
        try {
            CtClass item = HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item");
            CtClass[] parameters = { };
            CtMethod method = item.getMethod("getSizeMod", Descriptor.ofMethod(CtPrimitiveType.floatType, parameters));
            method.insertBefore("{ if (this.getTemplateId() == 731) return 10.0f; }");
        } catch (CannotCompileException | NotFoundException e) {
            throw new HookException(e);
        }
    }
    
}
