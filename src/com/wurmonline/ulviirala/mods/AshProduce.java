package com.wurmonline.ulviirala.mods;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

/**
 * Enables the generation of ash in specified template IDs like forges with
 * a percentage chance.
 */
public class AshProduce implements WurmServerMod, PreInitable, Configurable {
    private static final Logger _Logger = Logger.getLogger(AshProduce.class.getName());
    
    private int _AshChance = 60; // Default percentage chance.
    private int _AshStacks = 1; // Default max ash stacks in container.
    private int _AshAmount = 1; // Default pieces of ash per successful tick.
    
    // Map of what templateID has a specific chance and stack limit.
    private HashMap<Integer, Integer> _Chances = new HashMap<Integer, Integer>();
    private HashMap<Integer, Integer> _Stacks = new HashMap<Integer, Integer>();
    
    @Override
    public void preInit() {
        _Logger.info("Initialising AshProduce 1.4.1.");

        /**
         * As messy and convoluted as this code looks, profiling suggests that
         * on average, one pass takes 0.000n milliseconds. That's n/1000th of
         * a millisecond, or 0.0000000n seconds (on my system). Much of verbose 
         * high level code ends up being the same in javassembler as well,
         * often due to compiler optimisation.
         * 
         * If you're reading this and have a solid suggestion on improving this
         * code's performance, feel free to post in the thread or PM me.
         */
        
        StringBuilder sb = new StringBuilder();
        
        sb.append("int ashChance = _AshChances.nextInt(100);");
        
        // if (random chance is met AND for this template ID)
        sb.append("if (");
        Iterator iterator = _Chances.entrySet().iterator();
        while (true) {
            Entry<Integer,Integer> next = (Entry<Integer,Integer>)iterator.next();
            sb.append(String.format("(%d >= ashChance && this.getTemplateId() == %d)", next.getValue(), next.getKey()));
            
            if (!iterator.hasNext())
                break;
            
            sb.append(" || ");
        }
        sb.append(") {");
        
        sb.append("com.wurmonline.server.items.Item[] forgeItems = this.getItemsAsArray();");
        
        // counting ash stacks in forge while iterating
        sb.append("int ashStacks = 0;");
        
        // this much ash needs to be added in total
        sb.append("int ashWeightToAdd = " + String.valueOf(100 * _AshAmount) + ";");
        
        // Iterates through items in forge/container
        sb.append("for (int iForge = 0; iForge < forgeItems.length; iForge++) {");
        
        // Skip if item is not ash, doesn't need to be counted or anything.
        sb.append("    if (forgeItems[iForge].getTemplateId() != 141) continue;");
        
        // Pull into local variable, so we don't have to push iForge before getting array element.
        // I'm not sure if loading a local variable is faster.
        sb.append("    com.wurmonline.server.items.Item forgeItem = forgeItems[iForge];");
        
        // current ash item's weight.
        sb.append("    int ashWeight = forgeItem.getWeightGrams();");
        
        // count the stack.
        sb.append("    ashStacks++;");
        
        // if this ash item's weight is already at its maximum, skip it.
        sb.append("    if (ashWeight >= 6400) continue;");
        
        // if (templateID = value AND found stacks is greater than allowed stacks)
        sb.append("if (");
        for (Entry<Integer, Integer> entry : _Stacks.entrySet())
            sb.append(String.format("(this.getTemplateId() == %d && ashStacks > %d) ||", entry.getKey(), entry.getValue()));
        sb.delete(sb.lastIndexOf("||"), sb.length());
        // set weight to add to 0, no new stacks are created, and it's first in the evaluation order later
        sb.append(") { ashWeightToAdd = 0; break; }"); 
        
        // this much ash remains after adding ash to the current ash item.
        sb.append("    int ashRemainder = Math.max(0, ashWeightToAdd - (6400 - ashWeight));");
        
        // this is how much we'll add to the current ash item.
        sb.append("    int actualAddWeight = ashWeightToAdd - ashRemainder;");
        
        // average QL like vanilla Wurm.
        sb.append("    float newAshQl = forgeItem.getCurrentQualityLevel() * ashWeight + this.getCurrentQualityLevel() * actualAddWeight;"); 
        sb.append("    forgeItem.setWeight(Math.min(6400, ashWeight + actualAddWeight), true);");
        sb.append("    forgeItem.setQualityLevel(newAshQl / forgeItem.getWeightGrams());");
        sb.append("    forgeItem.setDamage(0.0f);");
        
        // this much ash remains to be added/
        sb.append("    ashWeightToAdd = ashRemainder;");
        sb.append("    if (ashRemainder <= 0) break;"); // stop looking for more existing ash stacks to fill.
        sb.append("}");

        // creates a new ash item, if there's more to be added, the forge has
        // less than 100 items, and the ash stack limit for the template ID
        // isn't hit yet.
        sb.append("if (ashWeightToAdd > 0 && this.getItemsAsArray().length < 100 && (");
        for (Entry<Integer, Integer> entry : _Stacks.entrySet())
            sb.append(String.format("(this.getTemplateId() == %d && ashStacks < %d) ||", entry.getKey(), entry.getValue()));
        sb.delete(sb.lastIndexOf("||"), sb.length());
        sb.append(")) { com.wurmonline.server.items.Item newAsh = com.wurmonline.server.items.ItemFactory.createItem(141, this.getCurrentQualityLevel(), null);"
                + "     if (!this.insertItem(newAsh, true)) com.wurmonline.server.Items.destroyItem(newAsh.getWurmId());"
                + "   }");
        sb.append("}");
        
        /* Example:
                int ashChance = _AshChances.nextInt(100);
                if ((65 >= ashChance && this.getTemplateId() == 178) || (70 >= ashChance && this.getTemplateId() == 180) || (95 >= ashChance && this.getTemplateId() == 1028) || (60 >= ashChance && this.getTemplateId() == 37) || (100 >= ashChance && this.getTemplateId() == 1023)) {
                    com.wurmonline.server.items.Item[] forgeItems = this.getItemsAsArray();
                    int ashStacks = 0;
                    int ashWeightToAdd = 100;
                    for (int iForge = 0; iForge < forgeItems.length; iForge++) {
                        if (forgeItems[iForge].getTemplateId() != 141) {
                            continue;
                        }
                        com.wurmonline.server.items.Item forgeItem = forgeItems[iForge];
                        int ashWeight = forgeItem.getWeightGrams();
                        ashStacks++;
                        if (ashWeight >= 6400) {
                            continue;
                        }
                        if ((this.getTemplateId() == 178 && ashStacks > 2) || (this.getTemplateId() == 180 && ashStacks > 3) || (this.getTemplateId() == 1028 && ashStacks > 1) || (this.getTemplateId() == 37 && ashStacks > 1) || (this.getTemplateId() == 1023 && ashStacks > 4)) {
                            ashWeightToAdd = 0;
                            break;
                        }
                        int ashRemainder = Math.max(0, ashWeightToAdd - (6400 - ashWeight));
                        int actualAddWeight = ashWeightToAdd - ashRemainder;
                        float newAshQl = forgeItem.getCurrentQualityLevel() * ashWeight + this.getCurrentQualityLevel() * actualAddWeight;
                        forgeItem.setWeight(Math.min(6400, ashWeight + actualAddWeight), true);
                        forgeItem.setQualityLevel(newAshQl / forgeItem.getWeightGrams());
                        forgeItem.setDamage(0.0f);
                        ashWeightToAdd = ashRemainder;
                        if (ashRemainder <= 0) {
                            break;
                        }
                    }
                    if (ashWeightToAdd > 0 && this.getItemsAsArray().length < 100 && ((this.getTemplateId() == 178 && ashStacks < 2) || (this.getTemplateId() == 180 && ashStacks < 3) || (this.getTemplateId() == 1028 && ashStacks < 1) || (this.getTemplateId() == 37 && ashStacks < 1) || (this.getTemplateId() == 1023 && ashStacks < 4))) {
                        com.wurmonline.server.items.Item newAsh = com.wurmonline.server.items.ItemFactory.createItem(141, this.getCurrentQualityLevel(), null);
                        if (!this.insertItem(newAsh, true)) {
                            com.wurmonline.server.Items.destroyItem(newAsh.getWurmId());
                        }
                    }
                }
        */
        
        try {
            CtClass ctClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item");
            ctClass.addField(new CtField(HookManager.getInstance().getClassPool().get("java.util.Random"), "_AshChances", ctClass), "new java.util.Random();");
            CtMethod ctMethod = ctClass.getMethod("coolOutSideItem", "(ZZ)V");
            ctMethod.instrument(new ExprEditor() {
                @Override
                public void edit (MethodCall methodCall) throws CannotCompileException {
                    if (methodCall.getMethodName().equals("getTemplateId"))
                        ctMethod.insertAt(methodCall.getLineNumber(), sb.toString());
                }
            });
        } catch (CannotCompileException | NotFoundException ex) {
            Logger.getLogger(AshProduce.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void configure(Properties properties) {
        _Logger.info("Loading configuration.");
        
        _AshChance = Math.max(1, Integer.valueOf(properties.getProperty("ashChance", String.valueOf(_AshChance))));
        if (_AshChance < 0 || _AshChance > 100)
            _Logger.log(Level.WARNING, "Values for ash chance should range from 0 to 100, current value: {0}.", _AshChance);
        _Logger.log(Level.INFO, "Global ash produce chance is {0}%.", String.valueOf(100 - _AshChance));
        
        _AshStacks = Math.max(0, Math.min(100, Integer.valueOf(properties.getProperty("ashStacks", String.valueOf(_AshStacks)))));
        _Logger.log(Level.INFO, "Global ash stacks limit is {0}.", _AshStacks);

        _AshAmount = Math.max(1, Math.min(64, Integer.valueOf(properties.getProperty("ashAmount", String.valueOf(_AshAmount)))));
        _Logger.log(Level.INFO, "Global ash amount per tick is {0}.", _AshAmount);
        
        String templateValues = properties.getProperty("ashProducers", "37,178,180,1023,1028");
        try {
            String[] producers = templateValues.split(",");
            
            for (String producer : producers) {
                String[] producerValues = producer.split(":");
                
                int producerID = -1, producerChance = -1, producerStacks = -1;
                
                for (int i = 0; i < producerValues.length; i++) {
                    switch (i) {
                        case 0:
                            producerID = Integer.valueOf(producerValues[i]);
                            break;
                        case 1:
                            if (producerValues[i].length() > 0)
                                producerChance = Integer.valueOf(producerValues[i]);
                            
                            if (producerChance < 0 || producerChance > 100)
                                _Logger.log(Level.WARNING, "Values for ash chance on specific templates should range from 0 to 100, value given for {0} was {1}%.", 
                                        new Object[] { producerID, producerChance });
                            break;
                        case 2:
                            producerStacks = Integer.valueOf(producerValues[i]);
                            
                            if (producerStacks < 1 || producerStacks > 64) {
                                _Logger.log(Level.WARNING, "Value for ash stacks on specific templates should range from 1 to 100, value given for {0} was {1}. Using default of {2} instead.", 
                                        new Object[] { producerID, producerStacks, _AshStacks });
                                producerStacks = -1;
                            }
                            break;
                        default:
                            _Logger.log(Level.WARNING,
                                    "Bogus value in ash producer string, there are too many fields in value #{0}. Given string was \"{1}\".",
                                    new Object[] { i + 1, templateValues });
                            break;
                    }
                }
                
                _Chances.put(producerID, producerChance == -1 ? _AshChance : producerChance);
                _Stacks.put(producerID, producerStacks == -1 ? _AshStacks : producerStacks);
                _Logger.log(Level.INFO, "Ash producer ID {0} will no produce {1} stacks of ash with {2} % chance per tick.",
                        new Object[] { producerID, _Stacks.get(producerID), _Chances.get(producerID) });
            }
        }
        catch (Exception e) {
            _Logger.log(Level.SEVERE, "Template ID values could not be parsed. ashProducers string was {0}.", templateValues);
            _Logger.log(Level.SEVERE, null, e);
        }
        
        _Logger.info("Configuration loaded.");
    }
}
