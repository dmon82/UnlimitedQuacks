package com.wurmonline.ulviirala.mods;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.CtPrimitiveType;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.Descriptor;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

/**
 *
 * Changes the capacity of newly created bulk containers, due to their size
 * being set at creation and saved to the database, a statement is required
 * to change those.
 * 
 * Also changes the number of items that fit into crates, also affects existing
 * crates due to the way their capacity is handled in the Item class.
 */
public class DoubleBulkCapacity implements WurmServerMod, PreInitable, Configurable {
    // Multiplies the capacity by this number.
    int _CrateFactor = 2;
    
    // The cube root for the capacity of bulk storage containers.
    int _BulkSize = 400;
    
    @Override
    public void preInit() {
        ModifyCrateCapacity();
        ModifyBulkCapacity();
    }
    
    @Override
    public void configure(Properties properties) {
        /**
         * Sanitise the values from 1 to 32767, so we can keep pushing a signed
         * short on the stack for the IMUL operation.
         */
        _CrateFactor = Integer.valueOf(properties.getProperty("crateFactor", String.valueOf(_CrateFactor)));
        _CrateFactor = Math.max(1, Math.min(32767, _CrateFactor));
        Logger.getLogger(DoubleBulkCapacity.class.getName()).log(Level.INFO, String.format("Crates will be resized with factor %d to %d and %d respectively.", _CrateFactor, 150 * _CrateFactor, 300 * _CrateFactor));
        
        /**
         * Sanitise values to 100^3 to 1290^3, because 2^31 is the upper bound
         * for signed 32 bit integers, so we cap it at the cube root of that.
         */
        _BulkSize = Integer.valueOf(properties.getProperty("bulkSize", String.valueOf(_BulkSize)));
        _BulkSize = Math.max(100, Math.min( (int)(Math.cbrt(Math.pow(2, 31) - 1)), _BulkSize));
        Logger.getLogger(DoubleBulkCapacity.class.getName()).log(Level.INFO, String.format("Bulk containers will have a capacity of %d.", (int)Math.pow(_BulkSize, 3) / 1000));
    }
    
    private void ModifyBulkCapacity() {
        try {
            CtClass ctClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.ItemTemplate");
            for (CtConstructor ctor : ctClass.getConstructors()) {
                if (ctor.getParameterTypes().length == 26) {
                    //ctor.insertBeforeBody("{ if ($1 == 661 || $1 == 662) { $15 = 400; $16 = 400; $17 = 400 } }");
                    ctor.insertBeforeBody(String.format("{ if ($1 == 661 || $1 == 662) { $15 = %1$d; $16 = %1$d; $17 = %1$d; } }", _BulkSize));
                }
            }
        } catch (CannotCompileException | NotFoundException ex) {
            Logger.getLogger(DoubleBulkCapacity.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    
    private void ModifyCrateCapacity() {
        try {
            CtClass ctClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item");
            CtClass[] parameters = new CtClass[0];
            CtMethod ctMethod = ctClass.getMethod("getRemainingCrateSpace", Descriptor.ofMethod(CtPrimitiveType.intType, parameters));
            CodeIterator iterator = ctMethod.getMethodInfo().getCodeAttribute().iterator();
            
            while (iterator.hasNext()) {
                int index = iterator.next();
                int op = iterator.byteAt(index);
                if (op == javassist.bytecode.Opcode.SIPUSH) {
                    int value = iterator.s16bitAt(index + 1);
                    /**
                     * Attempts to locate this code:
                     * if (this.template.templateId == 852) { return 300 - count; }
                     * if (this.template.templateId == 851) { return 150 - count; }
                     * 
                     * Multiplies the values 300 and 150 by 2, inserting the following byte code:
                     * SIPUSH value
                     * IMUL
                     */
                    if (value == 150 || value == 300)
                        iterator.insertAt(iterator.next(), new byte[] { 0x11, (byte)(_CrateFactor >> 8), (byte)(_CrateFactor & 0x0F), 0x68 });
                }
            }
        } catch (NotFoundException | BadBytecode ex) {
            Logger.getLogger(DoubleBulkCapacity.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
