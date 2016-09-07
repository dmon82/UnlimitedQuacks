package com.wurmonline.ulviirala.mods;

import com.wurmonline.server.creatures.Creature;
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
 * Aims to add hints for completing some epic missions.
 */
public class EpicMissionHints implements WurmServerMod, PreInitable {
    @Override
    public void preInit() {
        TreeHint();
        TraitorHint();
    }

    /**
     * Makes traitor creatures show up when tracking.
     */
    protected void TraitorHint() {
        try {
            Logger.getLogger(EpicMissionHints.class.getName()).log(Level.INFO, "Initializing traitor hints");
            
            CtClass ctClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.MethodsCreatures");
            CtClass[] parameters = new CtClass[] {
                HookManager.getInstance().getClassPool().get("com.wurmonline.server.creatures.Creature"),
                CtPrimitiveType.intType,
                CtPrimitiveType.intType,
                CtPrimitiveType.intType,
                CtPrimitiveType.floatType
            };
            CtMethod ctMethod = ctClass.getMethod("track", Descriptor.ofMethod(CtPrimitiveType.booleanType, parameters));
            ctMethod.instrument(new ExprEditor() { 
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    if (methodCall.getMethodName().equals("getTracksFor")) {
                        ctMethod.insertAt(methodCall.getLineNumber(), 
                                "{ com.wurmonline.server.creatures.Creature[] traitors = com.wurmonline.server.creatures.Creatures.getInstance().getCreatures();" +
                                        "for (int iTraitor = 0; iTraitor < traitors.length; iTraitor++) {" +
                                        "  if (traitors[iTraitor].getName().indexOf(\"traitor\") < 0) continue;" +
                                        
                                        "  int traitorDirectionIndex = com.wurmonline.server.behaviours.MethodsCreatures.getDir(performer, traitors[iTraitor].getCurrentTile().tilex, traitors[iTraitor].getCurrentTile().tiley);" +
                                        "  String traitorDirection = com.wurmonline.server.behaviours.MethodsCreatures.getLocationStringFor(performer.getStatus().getRotation(), traitorDirectionIndex, \"you\");" +
                                        
                                        "  int traitorDistance = Math.max(Math.abs(performer.getTileX() - traitors[iTraitor].getCurrentTile().tilex), Math.abs(performer.getTileY() - traitors[iTraitor].getCurrentTile().tiley));" +
                                        "  String traitorDistance = com.wurmonline.server.endgames.EndGameItems.getDistanceString(traitorDistance, traitors[iTraitor].getName(), traitorDirection, false);" +
                                        "  if (!traitors[iTraitor].isOnSurface()) performer.getCommunicator().sendNormalServerMessage(traitorDistance + \" It is below ground.\");" +
                                        "  else performer.getCommunicator().sendNormalServerMessage(traitorDistance);" +
                                        "}" +
                                "}"
                        );
                    }
                }
            });
        } catch (NotFoundException | CannotCompileException ex) {
            Logger.getLogger(EpicMissionHints.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    
    /**
    * Makes trees that are to be cut down centered on tile and, pre-set them to 69 damage.
    */
    protected void TreeHint() {
        try {
            Logger.getLogger(EpicMissionHints.class.getName()).log(Level.INFO, "Initialising epic mission cut tree hint.");
            
            CtClass ctClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.epic.EpicServerStatus");
            CtClass[] parameters = new CtClass[] {
                HookManager.getInstance().getClassPool().get("com.wurmonline.server.tutorial.Mission"),
                CtPrimitiveType.byteType,
                CtPrimitiveType.intType,
                HookManager.getInstance().getClassPool().get("java.lang.String"),
                CtPrimitiveType.intType,
                CtPrimitiveType.booleanType,
                CtPrimitiveType.intType
            };
            CtMethod ctMethod = ctClass.getMethod("createUseTileMission", Descriptor.ofMethod(CtPrimitiveType.booleanType, parameters));
            
            // inserts to lines of code before a unique method call.
            ctMethod.instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    if (methodCall.getMethodName().equals("getTileName"))
                        ctMethod.insertAt(methodCall.getLineNumber(), "{ com.wurmonline.server.Server.setWorldResource(targTile.getTileX(), targTile.getTileY(), 69); com.wurmonline.server.Server.setSurfaceTile(targTile.getTileX(), targTile.getTileY(), com.wurmonline.mesh.Tiles.decodeHeight(com.wurmonline.server.Server.surfaceMesh.getTile(targTile.getTileX(), targTile.getTileY())), com.wurmonline.mesh.Tiles.decodeType(com.wurmonline.server.Server.surfaceMesh.getTile(targTile.getTileX(), targTile.getTileY())), com.wurmonline.mesh.Tiles.encodeTreeData(com.wurmonline.mesh.FoliageAge.OLD_ONE, false, true, com.wurmonline.mesh.GrassData.GrowthTreeStage.SHORT)); } ");
                }
            });
        } catch (CannotCompileException | NotFoundException ex) {
            Logger.getLogger(EpicMissionHints.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
