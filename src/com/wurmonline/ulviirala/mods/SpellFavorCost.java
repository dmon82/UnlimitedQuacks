package com.wurmonline.ulviirala.mods;

import com.wurmonline.server.deities.Deities;
import com.wurmonline.server.deities.Deity;
import com.wurmonline.server.spells.Spell;
import java.lang.reflect.Field;
import java.util.Properties;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

/**
 * UNTESTED WITH 1.1.1.1
 * 
 * Changes the spell cost of all priest spells, diving them by a factor,
 * though the spell cost will always be at least 1 favor.
 */
public class SpellFavorCost implements WurmServerMod, ServerStartedListener, Configurable {
    private int _Factor = 10;
    
    @Override
    public void configure(Properties properties) {
        _Factor = Integer.valueOf(properties.getProperty("Factor", Integer.toString(_Factor)));
        
        // Sanitise value. Upper bound doesn't matter here, spell cost will
        // always be 1 or higher, set my Math.Max(1, n) where n is new cost.
        if (_Factor < 1)
            _Factor = 1;
    }
    
    @Override
    public void onServerStarted() {
        try {
            Field costField = ReflectionUtil.getField(Spell.class, "cost");

            for (Deity deity : Deities.getDeities()) {
                for (Spell spell : deity.getSpells()) {
                    int oldCost = spell.getCost(false);
                    int newCost = Math.max(1, oldCost / 10);

                    if (oldCost != newCost) {
                        ReflectionUtil.setPrivateField(spell, costField, newCost);
                    }
                }
            }
        } catch (IllegalArgumentException | IllegalAccessException | ClassCastException | NoSuchFieldException ex) {
            throw new HookException(ex);
        }
    }
}
