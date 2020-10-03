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
import javassist.expr.FieldAccess;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

/**
 * Uses PvE surface mining slope limits on PvP servers, by changing the code
 * in the relevant conditional statement, so that the server is handled like
 * a non-PvP server.
 */
public class PvpSurfaceMiningSlope implements WurmServerMod, PreInitable {
    @Override
    public void preInit() {
        try {
            CtClass ctClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.TileRockBehaviour");
            
            // public static boolean cannotMineSlope(Creature performer, Skill mining, int digTilex, int digTiley) {
            CtClass[] parameters = new CtClass[] {
                HookManager.getInstance().getClassPool().get("com.wurmonline.server.creatures.Creature"),
                HookManager.getInstance().getClassPool().get("com.wurmonline.server.skills.Skill"),
                CtPrimitiveType.intType,
                CtPrimitiveType.intType
            };
            
            CtMethod ctMethod = ctClass.getDeclaredMethod("cannotMineSlope");
            ctMethod.instrument(new ExprEditor() { 
                @Override
                public void edit(FieldAccess fieldAccess) throws CannotCompileException {
                    String fieldName = fieldAccess.getFieldName();
                    
                    if (fieldName.equals("PVPSERVER")) {
                        fieldAccess.replace("$_ = false;");
                        Logger.getLogger(PvpSurfaceMiningSlope.class.getName()).log(Level.INFO, "Patched PvP server statement for surface mining.");
                    }
                }
            });
    
        } catch (NotFoundException | CannotCompileException ex) {
            Logger.getLogger(PvpSurfaceMiningSlope.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
