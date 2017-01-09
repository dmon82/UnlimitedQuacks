package com.wurmonline.ulviirala.mods;

import java.util.Arrays;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtPrimitiveType;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Descriptor;
import javassist.bytecode.LocalVariableAttribute;
import javassist.bytecode.Opcode;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

/**
 * Aims to add hints for completing some epic missions, and add some control
 * over the types of missions created.
 */
public class EpicMissionHints implements WurmServerMod, PreInitable, Configurable {
    private static final Logger _Logger = Logger.getLogger(EpicMissionHints.class.getName());
    
    @Override
    public void preInit() {
        if (_UseTreeHints)
            TreeHint();
        
        if (_UseTraitorHints)
            TraitorHint();
        
        try {
            MissionTypes();
        }
        catch (Exception e) {
            _Logger.log(Level.SEVERE, "Mission types options could not be fully initialised.");
            _Logger.log(Level.SEVERE, null, e);
        }
    }

    private boolean _UseTraitorHints = true;
    private boolean _UseTreeHints = true;
    /**
     * A word on required numbers of missions (e.g. kill creatures, some number
     * of players must do something et cetera).
     * 
     * Missions have a difficulty number that is used as a multiplier. The
     * difficulty of a mission can be 0, but will always default to at least 1
     * in that case.
     * 
     * Other missions require a number of players, that is based on the amount
     * of "active" players in the kingdom. That is character, that have logged
     * in within the last 7 real life days. It's between 1 and 1/5 (a fifth) of
     * the kingdom's population.
     * 
     * The exact number of required items is never saved in the database,
     * instead it will save a floating point number, that represents an
     * increment of 1. Don't look at me, I didn't do that.
     */
    
    /**
     * See below.
     * 
     * This enables the possibility of all mission types. If a server is EPIC,
     * is a HOMESERVER but has NO deity that favours the kingdom (e.g. Freedom
     * Isles), server will never get any other mission type than the ones
     * mentioned for _EnemyServerMissions.
     */
    private boolean _UseAllMissionTypes = false;
    
    /**
     * An enemy home server is, an EPIC server, is a HOMESERVER, and the deity
     * this mission is for does NOT favour this kingdom.
     * 
     * Only "use guard tower", settlement, creature kill, and creature sacrifice
     * missions are generated on this server, for this deity.
     */
    private int[] _EnemyServerMissions = new int[] { 0, 1, 2, 3 };

    /**
     * There are 8 mission creations for these, "use tile missions" (cut tree),
     * and creature kill missions have twice as much chance to generate, twice
     * as much chance for a sacrifice decent item mission unless it's a
     * homeserver, then it's twice a chance that it's a 2:1 chance to be a
     * build mission, instead of a sacrifice decent item mission.
     * 
     * Please refer to com.wurmonline.server.epic.EpicServerStatus in the
     * generateNewMissionForEpicEntity method.
     */
    private int[] _OtherServerMissions = new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
    
    /* A number of players must use a certain item on a certain
        guard tower, to perform some arbitrary activity.
    
        The number of items required depends on the number of
        "active" players in the kingdom. "Active" players are
        player characters that have logged in within the last
        7 days.
    */
    private boolean _UseGuardTowerMissions = true;
    private int _MaxUseGuardTowerPerformers = -1; // No limit (server default).
    
    // Settlement missions are drain token missions.
    private boolean _UseSettlementMissions = true;
    
    /* A creature kill mission that has "many" set to false,
        makes it a traitor kill mission, which is basically
        a creature kill mission with a required count of 1,
        but requires one specific creature. This mission breaks
        by default on a server shutdown/restart.
    
        If the kill count limit is -1, the server default will
        execute, and no changes will applied by THIS mod.
    
        A count limit of 1 does not convert it to a traitor mission,
        it will be a kill requirement of 1, but applies to any
        creature of the type.
    */
    private boolean _UseCreatureKillMissions = true;
    private boolean _UseOnlyTraitorMissions = false;
    private boolean _NeverUseTraitorMissions = false;
    private int _CreatureKillCountLimit = -1; // No limit (server default).

    /* Sacrifice creatures with a sacrifical knife, required count depends on
        the mission's difficulty and can be anywhere from 1 to half of the
        creature's population on the server.
    */
    private boolean _UseCreatureSacrificeMissions = true;
    private int _SacrificeCreatureCountLimit = -1;
    
    // A target item can be a shrine, pylon, obelisk, temple, or spirit game.
    private boolean _UseBuildTargetItemMissions = true;
    
    // Perform some arbitrary ritual with an item or artifact.
    private boolean _UseTargetItemMissions = true;
    private int _TargetItemMissionLimit = -1;
    
    /* Cut down a tree within the first 6 tiles of a settlement perimeter,
        or 3 - 10 tiles around a guard tower. */
    private boolean _UseTileMissions = true;
    
    /* Create a certain item. At least 10, or 10 times the mission's difficulty,
        if an item has more than 100 parts, it's either 1 or equal to the
        mission's difficulty.
    */
    private boolean _UseBuildMissions = true;
    private int _BuildCountLimit = -1; // Items with 100 or less parts.
    private int _BuildLargeCountLimit = -1; // Items with over 100 parts.
    
    /* Sacrifice "decent" (QL 30+) items or N of "the hidden" items.
        You need to sacrifice 10-14 times the mission's difficulty amount.
        If it's food, the amount required is multiplied by three.
    
        Otherwise, the amount of hidden items scales with map size,
        which is mission difficulty * 2 (at least 1), times world size / 500
        (at least 2). So a 2048 tile map is (difficulty*2) * 4.
    */
    private boolean _UseSacrificeMissions = true;
    private int _SacrificeItemCountLimit = -1;
    private int _SacrificeFoodMultiplier = -1;
    private int _HiddenItemsCountLimit = -1;
    
    /* Give an item to an avatar. The amount of players required depends on
        "active" players (last login within the last 7 days).
    */
    private boolean _UseGiveMissions = true;
    private int _GiveCountLimit = -1;

    @Override
    public void configure(Properties properties) {
        Logger.getLogger(EpicMissionHints.class.getName()).log(Level.INFO, "Loading epic mission hints config.");
        
        _UseTraitorHints = Boolean.valueOf(properties.getProperty("useTraitorHints", String.valueOf(_UseTraitorHints)));
        _Logger.log(Level.INFO, "Use traitor hints = #{0}", _UseTraitorHints);
        
        _UseTreeHints = Boolean.valueOf(properties.getProperty("useTreeHints", String.valueOf(_UseTreeHints)));
        _Logger.log(Level.INFO, "Use tree hints = #{0}", _UseTreeHints);
        
        String values = properties.getProperty("otherServerMissions");
        if (values != null) {
            try {
                String[] fields = values.split(",");
                int[] missionTypes = new int[fields.length];

                for (int i = 0; i < fields.length; i++) {
                    missionTypes[i] = Integer.parseInt(fields[i]);
                    
                    if (missionTypes[i] < 0 || missionTypes[i] > 10)
                        throw new IndexOutOfBoundsException(
                            String.format("Valid numbers for home server missions are 0 through 10. Number given is #{0}$d in string %2$s.", missionTypes[i], values));
                }
                
                // Assign array of mission numbers.
                _OtherServerMissions = missionTypes;
                _Logger.log(Level.INFO, "Other server missions = #{0}", Arrays.toString(_OtherServerMissions));
            }
            catch (NumberFormatException nfe) {
                _Logger.log(Level.SEVERE, String.format("Error parsing home server mission types #{0}$s. No changes will be applied.", values));
                _Logger.log(Level.SEVERE, null, nfe);
            }
        }

        values = properties.getProperty("enemyServerMissions");
        if (values != null) {
            try {
                String[] fields = values.split(",");
                int[] missionTypes = new int[fields.length];

                for (int i = 0; i < fields.length; i++) {
                    missionTypes[i] = Integer.parseInt(fields[i]);
                
                    if (missionTypes[i] < 0 || missionTypes[i] > 3)
                        throw new IndexOutOfBoundsException(
                            String.format("Valid numbers for enemy server missions are 0 through 3. Number given is #{0}$d in string %2$s.", missionTypes[i], values));
                }
                
                // Assign mission numbers.
                _EnemyServerMissions = missionTypes;
                _Logger.log(Level.INFO, "Enemy server missions = #{0}", Arrays.toString(_EnemyServerMissions));
            }
            catch (NumberFormatException nfe) {
                _Logger.log(Level.SEVERE, String.format("Error parsing non-home server mission types #{0}$s. No changes will be applied.", values));
                _Logger.log(Level.SEVERE, null, nfe);
            }
        }
        
        _UseAllMissionTypes = Boolean.valueOf(properties.getProperty("useAllMissionTypes", String.valueOf(_UseAllMissionTypes)));
        _Logger.log(Level.INFO, "Use all mission types = #{0}", _UseAllMissionTypes);
        
        _UseGuardTowerMissions = Boolean.valueOf(properties.getProperty("useGuardTowerMissions", String.valueOf(_UseGuardTowerMissions)));
        _Logger.log(Level.INFO, "Use guard tower missions #{0}", _UseGuardTowerMissions);
        
        _MaxUseGuardTowerPerformers = Integer.valueOf(properties.getProperty("maxUseGuardTowerPerformers", String.valueOf(_MaxUseGuardTowerPerformers)));
        if (_MaxUseGuardTowerPerformers < 1) _MaxUseGuardTowerPerformers = -1;
        _Logger.log(Level.INFO, "Max 'use guard tower' performer = #{0}", _MaxUseGuardTowerPerformers);
        
        _UseSettlementMissions = Boolean.valueOf(properties.getProperty("useSettlementMissions", String.valueOf(_UseSettlementMissions)));
        _Logger.log(Level.INFO, "Use settlement missions = #{0}", _UseSettlementMissions);
        
        _UseCreatureKillMissions = Boolean.valueOf(properties.getProperty("useCreatureKillMissions", String.valueOf(_UseCreatureKillMissions)));
        _Logger.log(Level.INFO, "Use creature kill missions = #{0}", _UseCreatureKillMissions);
        
        _UseOnlyTraitorMissions = Boolean.valueOf(properties.getProperty("useOnlyTraitorMissions", String.valueOf(_UseOnlyTraitorMissions)));
        _Logger.log(Level.INFO, "Use only traitor missions = #{0} (takes precedence over next option if true)", _UseOnlyTraitorMissions);
        
        _NeverUseTraitorMissions = Boolean.valueOf(properties.getProperty("neverUseTraitorMissions", String.valueOf(_NeverUseTraitorMissions)));
        _Logger.log(Level.INFO, "Never use traitor missions = #{0} (previous option takes precedence if true)", _NeverUseTraitorMissions);
        
        _CreatureKillCountLimit = Integer.valueOf(properties.getProperty("creatureKillCountLimit", String.valueOf(_CreatureKillCountLimit)));
        if (_CreatureKillCountLimit < 1) _CreatureKillCountLimit = -1;
        _Logger.log(Level.INFO, "Creature kill count limit = #{0}", _CreatureKillCountLimit);
        
        _UseCreatureSacrificeMissions = Boolean.valueOf(properties.getProperty("useCreatureSacrificeMissions", String.valueOf(_UseCreatureSacrificeMissions)));
        _Logger.log(Level.INFO, "Use creature sacrifice missions = #{0}", _UseCreatureSacrificeMissions);
        
        _SacrificeCreatureCountLimit = Integer.valueOf(properties.getProperty("sacrificeCreatureCountLimit", String.valueOf(_SacrificeCreatureCountLimit)));
        if (_SacrificeCreatureCountLimit < 1 || _SacrificeCreatureCountLimit > 32767) _SacrificeCreatureCountLimit = -1;
        _Logger.log(Level.INFO, "Sacrifice creature count limit = #{0}", _SacrificeCreatureCountLimit);
        
        _UseBuildTargetItemMissions = Boolean.valueOf(properties.getProperty("useBuildTargetItemMissions", String.valueOf(_UseBuildTargetItemMissions)));
        _Logger.log(Level.INFO, "Use build target item (monument) missions = #{0}", _UseBuildTargetItemMissions);
        
        _UseTargetItemMissions = Boolean.valueOf(properties.getProperty("useTargetItemMissions", String.valueOf(_UseTargetItemMissions)));
        _Logger.log(Level.INFO, "Use target item (ritual) missions = #{0}", _UseTargetItemMissions);
        
        _TargetItemMissionLimit = Integer.valueOf(properties.getProperty("targetItemMissionLimit", String.valueOf(_TargetItemMissionLimit)));
        if (_TargetItemMissionLimit < 1) _TargetItemMissionLimit = -1;
        _Logger.log(Level.INFO, "Target item mission (performers/players) limit = #{0}", _TargetItemMissionLimit);
        
        _UseTileMissions = Boolean.valueOf(properties.getProperty("useTileMissions", String.valueOf(_UseTileMissions)));
        _Logger.log(Level.INFO, "Use tile (cut tree) missions = #{0}", _UseTileMissions);
        
        _UseBuildMissions = Boolean.valueOf(properties.getProperty("useBuildMissions", String.valueOf(_UseBuildMissions)));
        _Logger.log(Level.INFO, "Use build (craft item) missions = #{0}", _UseBuildMissions);
        
        _BuildCountLimit = Integer.valueOf(properties.getProperty("buildCountLimit", String.valueOf(_BuildCountLimit)));
        if (_BuildCountLimit < 1) _BuildCountLimit = -1;
        _Logger.log(Level.INFO, "Build count limit = #{0}", _BuildCountLimit);
        
        _BuildLargeCountLimit = Integer.valueOf(properties.getProperty("buildLargeCountLimit", String.valueOf(_BuildLargeCountLimit)));
        if (_BuildLargeCountLimit < 1) _BuildLargeCountLimit = -1;
        _Logger.log(Level.INFO, "Large item build count limit = #{0}", _BuildLargeCountLimit);
        
        _UseSacrificeMissions = Boolean.valueOf(properties.getProperty("useSacrificeMissions", String.valueOf(_UseSacrificeMissions)));
        _Logger.log(Level.INFO, "Use sacrifice missions = #{0}", _UseSacrificeMissions);
        
        _SacrificeItemCountLimit = Integer.valueOf(properties.getProperty("sacrificeItemCountLimit", String.valueOf(_SacrificeItemCountLimit)));
        if (_SacrificeItemCountLimit < 1) _SacrificeItemCountLimit = -1;
        _Logger.log(Level.INFO, "Sacrifice item count limit = #{0}", _SacrificeItemCountLimit);
        
        _SacrificeFoodMultiplier = Integer.valueOf(properties.getProperty("sacrificeFoodMultiplier", String.valueOf(_SacrificeFoodMultiplier)));
        if (_SacrificeFoodMultiplier < 1 || _SacrificeFoodMultiplier > 5) _SacrificeFoodMultiplier = -1;
        _Logger.log(Level.INFO, "Sacrifice food multiplier = #{0}", _SacrificeFoodMultiplier);
        
        _HiddenItemsCountLimit = Integer.valueOf(properties.getProperty("hiddenItemsCountLimit", String.valueOf(_HiddenItemsCountLimit)));
        if (_HiddenItemsCountLimit < 1) _HiddenItemsCountLimit = -1;
        _Logger.log(Level.INFO, "Hidden items (sacrifice) count limit = #{0}", _HiddenItemsCountLimit);
        
        _UseGiveMissions = Boolean.valueOf(properties.getProperty("useGiveMissions", String.valueOf(_UseGiveMissions)));
        _Logger.log(Level.INFO, "Use give (item to avatar) missions = #{0}", _UseGiveMissions);
        
        _GiveCountLimit = Integer.valueOf(properties.getProperty("giveCountLimit", String.valueOf(_GiveCountLimit)));
        if (_GiveCountLimit < 1) _GiveCountLimit = -1;
        _Logger.log(Level.INFO, "Give (required items/players) count limit = #{0}", _GiveCountLimit);
    }
    
    /**
     * Applies changes to epic mission generation according to the config file.
     * 
     * @throws NotFoundException
     * @throws CannotCompileException
     * @throws BadBytecode
     * @throws Exception 
     */
    protected void MissionTypes() throws NotFoundException, CannotCompileException, BadBytecode, Exception {
        _Logger.log(Level.INFO, "Initializing mission types settings");

        CtClass ctClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.epic.EpicServerStatus");
        
        /**
         * Get indexes in the constant pool for method calls to Math.min(II)I and Math.max(II)I,
         * or if none are found, add them, so they can be called later. They will always be
         * invokestatic operations (0x0B) to a 2-byte index in the constant pool of the class.
         * 
         * This way, it's less likely to break compatability with other mods, and updates to the
         * WU server software.
         */
        int mathMaxIndex = -1;
        int mathMinIndex = -1;
        int rngIndex = -1;
        int randIntIndex = -1;
        
        ConstPool constPool = ctClass.getClassFile().getConstPool();

        for (int i = 1; i < constPool.getSize(); i++) {
            if (constPool.getTag(i) == ConstPool.CONST_Methodref) {
                if (constPool.getMethodrefClassName(i).equals("java.lang.Math") && constPool.getMethodrefName(i).equals("min")) {
                    mathMinIndex = i;
                    _Logger.log(Level.INFO, "java.lang.Math.min entry found in constant pool at index #{0}", i);
                }
                else if (constPool.getMethodrefClassName(i).equals("java.lang.Math") && constPool.getMethodrefName(i).equals("max")) {
                    mathMaxIndex = i;
                    _Logger.log(Level.INFO, "java.lang.Math.max entry found in constant pool at index #{0}", i);
                }
                else if (constPool.getMethodrefClassName(i).equals("java.util.Random") && constPool.getMethodrefName(i).equals("nextInt") && constPool.getMethodrefType(i).equals("(I)I")) {
                    randIntIndex = i;
                    _Logger.log(Level.INFO, "java.util.Random.nextInt entry found in constant pool at index #{0}", i);
                }
            }
            else if (constPool.getTag(i) == ConstPool.CONST_Fieldref) {
                if (constPool.getFieldrefName(i).equals("rand")) {
                    rngIndex = i;
                    _Logger.log(Level.INFO, "com.wurmonline.server.Server.rand entry found in constant pool at index #{0}", i);
                }
            }
        }

        if (mathMinIndex < 0) {
            _Logger.log(Level.INFO, "No entry for java.lang.Math.min found in constant pool, adding one.");
            mathMinIndex = constPool.addMethodrefInfo(constPool.addClassInfo("java.lang.Math"), "min", "(II)I");
            _Logger.log(Level.INFO, "java.lang.Math.min added to constant pool at #{0}", mathMinIndex);
        }
        
        if (mathMaxIndex < 0) {
            _Logger.log(Level.INFO, "No entry for java.lang.Math.max found in constant pool, adding one.");
            mathMaxIndex = constPool.addMethodrefInfo(constPool.addClassInfo("java.lang.Math"), "max", "(II)I");
            _Logger.log(Level.INFO, "java.lang.Math.max added to constant pool at #{0}", mathMaxIndex);
        }
        
        if (randIntIndex < 0 || rngIndex < 0)
            throw new Exception ("java.util.Random.nextInt and/or com.wurmonline.server.Server.rand field not found in constant pool. Has the class file changed?");

        // Main mission generation method.
        CtMethod generateMission = ctClass.getMethod("generateNewMissionForEpicEntity", "(ILjava/lang/String;IILjava/lang/String;ILjava/lang/String;Z)V");
        CodeIterator missionIter = generateMission.getMethodInfo().getCodeAttribute().iterator();
        // Start applying settings.
        {
            _Logger.log(Level.INFO, "Adding enemy and other server missions field to class.");
            
            StringBuilder sb = new StringBuilder();
            sb.append("new int[] { ");
            for (int i : _EnemyServerMissions)
                sb.append(String.format("%d,", i));
            sb.deleteCharAt(sb.lastIndexOf(","));
            sb.append(" };");
            ctClass.addField(new CtField(HookManager.getInstance().getClassPool().get("[I"), "_EnemyServerMissions", ctClass), sb.toString());
            int enemyFieldIndex = constPool.addFieldrefInfo(constPool.getThisClassInfo(), "_EnemyServerMissions", "[I");
            _Logger.log(Level.INFO, "Added EnemyFieldIndex as " + enemyFieldIndex);

            sb = new StringBuilder();
            sb.append("new int[] { ");
            for (int i : _OtherServerMissions)
                sb.append(String.format("%d,", i));
            sb.deleteCharAt(sb.lastIndexOf(","));
            sb.append(" };");
            ctClass.addField(
                    new CtField(
                            HookManager.getInstance().getClassPool().get("[I"),
                            "_OtherServerMissions", 
                            ctClass),
                    sb.toString());
            int otherFieldIndex = constPool.addFieldrefInfo(constPool.getThisClassInfo(), "_OtherServerMissions", "[I");
            _Logger.log(Level.INFO, "Added OtherFieldIndex as " + otherFieldIndex);

            // Applies mission type probabilities.
            _Logger.log(Level.INFO, "Looking up 'desired' and 'this' in local variable table.");
            
            int desiredIndex = -1;
            int thisIndex = -1;
            LocalVariableAttribute lva = (LocalVariableAttribute)generateMission.getMethodInfo().getCodeAttribute().getAttribute(LocalVariableAttribute.tag);
            for (int i = 0; (desiredIndex < 0 || thisIndex < 0) && i < lva.tableLength(); i++) {
                if (lva.variableName(i).equals("desired"))
                    desiredIndex = lva.index(i);
                else if (lva.variableName(i).equals("this"))
                    thisIndex = lva.index(i);
            }

            if (desiredIndex < 0)
                throw new Exception("Can't find variable 'desired' in 'generateNewMissionForEpicEntity'. Has the class file changed?");
            
            if (thisIndex < 0)
                throw new Exception("Can't find variable 'this' in 'generateNewMissionForEpicentity'. Has the class file changed?");
                
            _Logger.log(Level.INFO, "desiredIndex found at #{0}", desiredIndex);
            _Logger.log(Level.INFO, "thisIndex found at #{0}", thisIndex);
            
            Random rand = new Random();
            int istoreCount = 0;
            
            _Logger.log(Level.INFO, "Applying server and other missions field use switch-case.");
            
            while (missionIter.hasNext()) {
                int index =  missionIter.next();
                int opCode = missionIter.byteAt(index);
                
                // re-assigns the "desired" variable (desired mission type).
                if (opCode == Opcode.ISTORE && missionIter.byteAt(index + 1) == desiredIndex) {
                    // aload ARRAY 0x19 INDEX
                    // arraylength 0xBE
                    // invokevirtual int java.util.Random.nextInt(int) 0xB6 0x00 0x9C
                    // baload 0x33
                    // istore INDEX 0x36 {desiredIndex}
                    if (istoreCount == 0) {
                        _Logger.log(Level.INFO, "Inserting enemy server mission field at pos #{0}.", index);
                        
                        missionIter.insertAt(index + 2, new byte[] {
                            0x19, // aload (byte)localVariableIndex
                            (byte)thisIndex,
                            (byte)0xB4, // getfield (short)index
                            (byte)(enemyFieldIndex >> 8),
                            (byte)(enemyFieldIndex & 0xFF),
                            (byte)0xB2, // getstatic java.util.Random com.wurmonline.server.Server.rand
                            (byte)(rngIndex >> 8),
                            (byte)(rngIndex & 0xFF),
                            0x19, // aload (byte)localVariableIndex
                            (byte)thisIndex,
                            (byte)0xB4, // getfield (short)index
                            (byte)(enemyFieldIndex >> 8),
                            (byte)(enemyFieldIndex & 0xFF),
                            (byte)0xBE, //arraylength
                            (byte)0xB6, // invokevirtual (short)index
                            (byte)(randIntIndex >> 8),
                            (byte)(randIntIndex & 0xFF),
                            0x2E, // iaload
                            0x36, // istore
                            (byte)desiredIndex
                        });
                    }
                    else if (istoreCount == 1) {
                        _Logger.log(Level.INFO, "Inserting other server missions field at pos #{0}.", index);
                        missionIter.insertAt(index + 2, new byte[] {
                            0x19, // aload (byte)localVariableIndex for THIS
                            (byte)thisIndex,
                            (byte)0xB4, // getfield (short)index
                            (byte)(otherFieldIndex >> 8),
                            (byte)(otherFieldIndex & 0xFF),
                            (byte)0xB2, // getstatic java.util.Random com.wurmonline.server.Server.rand
                            (byte)(rngIndex >> 8),
                            (byte)(rngIndex & 0xFF),
                            0x19, // aload (byte)localVariableIndex
                            (byte)thisIndex,
                            (byte)0xB4, // getfield (short)index
                            (byte)(otherFieldIndex >> 8),
                            (byte)(otherFieldIndex & 0xFF),
                            (byte)0xBE, //arraylength
                            (byte)0xB6, // invokevirtual (short)index
                            (byte)(randIntIndex >> 8),
                            (byte)(randIntIndex & 0xFF),
                            0x2E, // iaload
                            0x36, // istore
                            (byte)desiredIndex
                        });
                    }
                    
                    istoreCount++;
                }
            }
        }
        
        if (_UseAllMissionTypes) {
            _Logger.info("Applying the use of all mission types.");
            
            try {
                missionIter.move(0);

                // Find local variable index of "enemyHomeServer" variable.
                int enemyHomeServerIndex = -1;
                LocalVariableAttribute locals = (LocalVariableAttribute)generateMission
                        .getMethodInfo()
                        .getCodeAttribute()
                        .getAttribute(LocalVariableAttribute.tag);
                for (int i = 0; enemyHomeServerIndex < 0 && i < locals.tableLength(); i++)
                    if (locals.variableName(i).equals("enemyHomeServer"))
                        enemyHomeServerIndex = locals.index(i);
                
                if (enemyHomeServerIndex < 0)
                    throw new Exception("Variable for 'enemyHomeServer' not found in 'generateNewMissionForEpicEntity' method. Has the class changed?");
                
                int lastLoadIndex = -1;
                
                while (missionIter.hasNext()) {
                    int index = missionIter.next();
                    int opCode = missionIter.byteAt(index);
                    
                    /**
                     * Overwrites the loading of a local variable, and replaces it with zero.
                     * Instead of using the "enemyHomeServer" variable, it will now use zero,
                     * and the following IFEQ operation will always jump to the switch-case
                     * with all mission types.
                     */
                    
                    if (opCode == Opcode.ISTORE && missionIter.byteAt(index + 1) == enemyHomeServerIndex)
                        lastLoadIndex = index;
                }
                
                missionIter.insertAt(lastLoadIndex, new byte[] { 0x57, 0x03,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0 });
                _Logger.log(Level.INFO, "UseAllMissionTypes has applied its changes at #{0}.", lastLoadIndex);
                
            }
            catch (Exception e) {
                _Logger.log(Level.SEVERE, "Can't override to USE ALL MISSION TYPES.");
                _Logger.log(Level.SEVERE, null, e);
            }
        }
        
        if (!_UseGuardTowerMissions) {
            _Logger.info("Disabling generation of use guard tower missions.");
            CtMethod towerMission = ctClass.getMethod("createUseGuardTowerMission", "(Lcom/wurmonline/server/tutorial/Mission;BILjava/lang/String;II)Z");
            towerMission.setBody("{ return false; }");
        }
        else if (_MaxUseGuardTowerPerformers > -1) {
            _Logger.info("Limiting the number of required guard tower ritual performers.");
            
            ctClass.getMethod("createUseGuardTowerMission", "(Lcom/wurmonline/server/tutorial/Mission;BILjava/lang/String;II)Z").instrument(
                new ExprEditor() { 
                    @Override
                    public void edit(MethodCall call) throws CannotCompileException {
                        /**
                         * Replace the only (vanilla code) call to the method, and uses the lowest number we have.
                         */
                        if (call.getClassName().equals("com.wurmonline.server.epic.EpicServerStatus") && call.getMethodName().equals("getNumberOfRequiredPlayers")) {
                            _Logger.log(Level.INFO, "Guard tower performer number found at line #{0}", call.getLineNumber());
                            call.replace("{ $_ = java.lang.Math.min( $proceed($$), " + _MaxUseGuardTowerPerformers + "); }");
                        }
                    }
                }
            );
        }
        
        /**
         * Implement settlement mission options.
         * */
        if (!_UseSettlementMissions) {
            _Logger.info("Disabling the generation of settlement drain token missions.");
            ctClass.getMethod("createSettlementMission", "(Lcom/wurmonline/server/tutorial/Mission;BILjava/lang/String;IIZ)Z").setBody("{ return false; }");
        }
        
        /**
         * Implement creature kill mission options.
         * */
        if (!_UseCreatureKillMissions) {
            _Logger.info("Disabling the generation of creature kill missions.");
            ctClass.getMethod("createKillCreatureMission", "(Lcom/wurmonline/server/tutorial/Mission;BILjava/lang/String;IZI)Z").setBody("{ return false; }");
        }
        else {
            // ONLY traitor mission option takes precedence over NEVER traitor missions
            if (_UseOnlyTraitorMissions) { // change the "many" argument to always false.
                _Logger.info("Creature kill missions will now always be traitor kill missions.");
                ctClass.getMethod("createKillCreatureMission", "(Lcom/wurmonline/server/tutorial/Mission;BILjava/lang/String;IZI)Z").setBody("{ $6 = false; }");
            }
            else if (_NeverUseTraitorMissions) { // change the "many" argument to always true.
                _Logger.info("Creature kill missions will never be traitor kill missions.");
                ctClass.getMethod("createKillCreatureMission", "(Lcom/wurmonline/server/tutorial/Mission;BILjava/lang/String;IZI)Z").insertBefore("{ $6 = true; }");
            }
            
            // Only apply a limit if non-traitor missions are possible
            if ((_NeverUseTraitorMissions || !_UseOnlyTraitorMissions) && _CreatureKillCountLimit > -1) {
                _Logger.info("Limiting the amount of creatures for kill missions.");
                
                ctClass.getMethod("createKillCreatureMission", "(Lcom/wurmonline/server/tutorial/Mission;BILjava/lang/String;IZI)Z").instrument(
                    new ExprEditor() { 
                        @Override
                        public void edit(MethodCall call) throws CannotCompileException {
                            // Replaces the only call (vanilla code) to use either our limit or the returned number.
                            if (call.getClassName().equals("java.lang.Math") && call.getMethodName().equals("min")) {
                                _Logger.log(Level.INFO, "Creature kill limit found at line #{0}.", call.getLineNumber());
                                call.replace("{ $_ = java.lang.Math.min( $proceed($$), " + _CreatureKillCountLimit + " ); }");
                            }
                        }
                    } 
                );
            }
        }

        /**
         * Implement creature sacrifice mission options.
         * */
        if (!_UseCreatureSacrificeMissions) {
            _Logger.info("Disabling the generation of sacrifice creature missions.");
            ctClass.getMethod("createSacrificeCreatureMission", "(Lcom/wurmonline/server/tutorial/Mission;BILjava/lang/String;II)Z").setBody("{ return false; }");
        }
        else if (_SacrificeCreatureCountLimit > -1) {
            _Logger.info("Limiting the amount of required creatures to sacrifice.");
            
            int requiredIndex = -1;
            LocalVariableAttribute locals = (LocalVariableAttribute)ctClass.getMethod("createSacrificeCreatureMission", "(Lcom/wurmonline/server/tutorial/Mission;BILjava/lang/String;II)Z")
                    .getMethodInfo()
                    .getCodeAttribute()
                    .getAttribute(LocalVariableAttribute.tag);
            for (int i = 0; requiredIndex < 0 && i < locals.tableLength(); i++)
                if (locals.variableName(i).equals("required"))
                    requiredIndex = locals.index(i);
            
            _Logger.log(Level.INFO, "'required' variable found in local variable table at #{0}", requiredIndex);
            
            CodeIterator iter = ctClass.getMethod("createSacrificeCreatureMission", "(Lcom/wurmonline/server/tutorial/Mission;BILjava/lang/String;II)Z")
                    .getMethodInfo()
                    .getCodeAttribute()
                    .iterator();
            
            int sacrificeSet = 0;
            
            while (iter.hasNext()) {
                int index = iter.next();

                /**
                 * After storing the result of the number required, insert bytecode to push
                 * our number limit on the stack, and perform a Math.min() operation, which
                 * will then end up on the stack instead for the next conditional statement.
                 */
                if (iter.byteAt(index) == Opcode.ISTORE && iter.byteAt(index + 1) == requiredIndex) {
                    _Logger.log(Level.INFO, "Found ISTORE operation at position #{0}", index);
                    
                    switch (sacrificeSet) {
                        case 0:
                            _Logger.log(Level.INFO, "Found first ISTORE at initial variable declaration, skipping pos #{0}.", index);
                            break;
                        case 1:
                            _Logger.log(Level.INFO, "Found second ISTORE, applying limit at pos #{0}.", index);
                            
                            // sipush <16 bit signed integer>
                            // invokestatic java.lang.Math.min(II)I
                            iter.insertAt(index, new byte[] {
                                0x11, // sipush
                                (byte)(_SacrificeCreatureCountLimit >> 8), // msb of the short
                                (byte)(_SacrificeCreatureCountLimit & 0xFF), // lsb
                                (byte)0xB8, // invokestatic
                                (byte)(mathMinIndex >> 8), // msb of the index in the constant pool of Math.min()
                                (byte)(mathMinIndex & 0xFF) }); // lsb
                            break;
                        default:
                            throw new Exception("Found more than one ISTORE <required> operation in createSacrificeCreatureMission. Mod conflict or method changed?");
                    }
                    
                    sacrificeSet++;
                }
            }
        }
        
        /**
         * Implement build target item mission options.
         */
        if (!_UseBuildTargetItemMissions) {
            _Logger.info("Disabling generation of build target item (monument) missions.");
            ctClass.getMethod("createBuildTargetItemMission", "(Lcom/wurmonline/server/tutorial/Mission;BILjava/lang/String;II)Z").setBody("{ return false; }");
        }
        
        /**
         * Implement target item mission options.
         */
        if (!_UseTargetItemMissions) {
            _Logger.info("Disabling generation of use target item (ritual) missions.");
            ctClass.getMethod("createUseTargetItemMission", "(Lcom/wurmonline/server/tutorial/Mission;BILjava/lang/String;IZI)Z").setBody("{ return false; }");
        }
        else if (_TargetItemMissionLimit > -1) {
            _Logger.info("Limiting number of players required to perform item ritual missions.");
            
            ctClass.getMethod("createUseTargetItemMission", "(Lcom/wurmonline/server/tutorial/Mission;BILjava/lang/String;IZI)Z").instrument(
                new ExprEditor() { 
                    @Override
                    public void edit(MethodCall call) throws CannotCompileException {
                        // Replaces the only call (vanilla code) to respect our number limit.
                        if (call.getClassName().equals("com.wurmonline.server.epic.EpicServerStatus") && call.getMethodName().equals("getNumberOfRequiredPlayers")) {
                            _Logger.log(Level.INFO, "Found method call at line #{0}", call.getLineNumber());
                            call.replace("{ $_ = Math.min( $proceed($$), " + _TargetItemMissionLimit + "); }");
                        }
                    }
                }
            );
        }
        
        /**
         * Implement tile mission (cut down tree) options.
         */
        if (!_UseTileMissions) {
            _Logger.info("Disabling generation of tile (cut tree) missions.");
            ctClass.getMethod("createUseTileMission", "(Lcom/wurmonline/server/tutorial/Mission;BILjava/lang/String;IZI)Z").setBody("{ return false; }");
        }
        
        /**
         * Implement item creation mission options.
         */
        if (!_UseBuildMissions) {
            _Logger.info("Disabling generation of build (create/craft) item missions.");
            ctClass.getMethod("createBuildMission", "(Lcom/wurmonline/server/tutorial/Mission;BILjava/lang/String;II)Z").setBody("{ return false; }");
        }
        else if (_BuildCountLimit > -1 || _BuildLargeCountLimit > -1) {
            _Logger.info("Limiting number of items to build (create/craft).");
            
            ctClass.getMethod("createBuildMission", "(Lcom/wurmonline/server/tutorial/Mission;BILjava/lang/String;II)Z").instrument(
                new ExprEditor() {
                    private int _Index = 0;
                    
                    @Override
                    public void edit(MethodCall call) throws CannotCompileException {
                        // Replaces the first and second (vanilla code) calls to Math.min(), to respect our limits.
                        if (call.getClassName().equals("java.lang.Math") && call.getMethodName().equals("max")) {
                            if (_Index == 0 && _BuildCountLimit > -1) {
                                _Logger.log(Level.INFO, "Found build count limit call at line #{0}.", call.getLineNumber());
                                call.replace("{ $_ = Math.min( $proceed($$), " + _BuildCountLimit + " ); }");
                            }
                            else if (_Index == 1 && _BuildLargeCountLimit > -1) {
                                _Logger.log(Level.INFO, "Found large item build count limit call at line #{0}.", call.getLineNumber());
                                call.replace("{ $_ = Math.min( $proceed($$), " + _BuildLargeCountLimit + " ); }");
                            }
                            
                            _Index++;
                        }
                    }
                }
            );
        }
        
        /**
         * Implement sacrifice (decent item) mission options.
         */
        if (!_UseSacrificeMissions) {
            _Logger.info("Disabling generation of sacrifice (decent quality) item missions.");
            ctClass.getMethod("createSacrificeMission", "(Lcom/wurmonline/server/tutorial/Mission;BILjava/lang/String;II)Z").setBody("{ return false; }");
        }
        else if (_SacrificeItemCountLimit > -1 || _SacrificeFoodMultiplier > -1 || _HiddenItemsCountLimit > -1) {
            _Logger.info("Limiting item sacrifice mission numbers.");
            // Identify variable indexes for "required" and "nums" we need to temper with.
            LocalVariableAttribute locals = (LocalVariableAttribute)ctClass.getMethod("createSacrificeMission", "(Lcom/wurmonline/server/tutorial/Mission;BILjava/lang/String;II)Z")
                    .getMethodInfo()
                    .getCodeAttribute()
                    .getAttribute(LocalVariableAttribute.tag);
            
            int requiredIndex = -1;
            int numsIndex = -1;
            
            for (int i = 0; (requiredIndex < 0 || numsIndex < 0) && i < locals.tableLength(); i++) {
                if (locals.variableName(i).equals("required"))
                    requiredIndex = locals.index(i);
                else if (locals.variableName(i).equals("nums"))
                    numsIndex = locals.index(i);
            }
            
            if (requiredIndex < 0)
                throw new Exception("Can't find variable 'required' in createSacrificeMission method.");
            
            if (numsIndex < 0)
                throw new Exception("Can't find variable 'nums' in createSacrificeMission method.");
            
            _Logger.log(Level.INFO, "requiredIndex found in local variable table at #{0}", requiredIndex);
            _Logger.log(Level.INFO, "numsIndex found in local variable table at #{0}", numsIndex);
            
            CodeIterator iter = ctClass.getMethod("createSacrificeMission", "(Lcom/wurmonline/server/tutorial/Mission;BILjava/lang/String;II)Z")
                    .getMethodInfo()
                    .getCodeAttribute()
                    .iterator();
            
            int assignCount = 0;
            
            while (iter.hasNext()) {
                int index = iter.next();
                
                if (iter.byteAt(index) == Opcode.ISTORE && iter.byteAt(index + 1) == requiredIndex) {
                    switch (assignCount) {
                        case 0:
                            if (_SacrificeItemCountLimit > -1) {
                                _Logger.log(Level.INFO, "Appplying item sacrifice limit at pos #{0}", index);

                                iter.insertAt(index, new byte[] {
                                    0x11, //sipush
                                    (byte)(_SacrificeItemCountLimit >> 8),
                                    (byte)(_SacrificeItemCountLimit & 0xFF),
                                    (byte)0xB8, // invokestatic
                                    (byte)(mathMinIndex >> 8),
                                    (byte)(mathMinIndex & 0xFF)
                                });
                            }
                            
                            assignCount++;
                            break;
                        case 1:
                            if (_SacrificeFoodMultiplier > -1) {
                                _Logger.log(Level.INFO, "Applying food sacrifice multiplier at pos #{0}", index);
                                
                                iter.insertAt(index, new byte[] {
                                    0x06, // iconst_3
                                    0x6C, // idiv
                                    (byte)(3 + _SacrificeFoodMultiplier), // iconst_0 == 3 + multiplier up to iconst_5 (0x08)
                                    0x68, // imul
                                });
                            }
                            
                            assignCount++;
                            break;
                        case 2:
                            /**
                             * Since we might have introduced a new lower limit, the required number is always
                             * divided by the number of components for the item, and a large enough number might
                             * result in requiring zero items.
                             * 
                             * We'll make sure we'll always need at least one, by performing a Math.max() if
                             * we've performed a change.
                             * */
                            if (_SacrificeItemCountLimit > -1) {
                                _Logger.log(Level.INFO, "Ensuring a minimum of 1 item to sacrifice at pos #{0}.", index);
                                iter.insertAt(index, new byte[] {
                                    0x04, // iconst_1
                                    (byte)0xB8, // invokestatic
                                    (byte)(mathMaxIndex >> 8),
                                    (byte)(mathMaxIndex & 0xFF)
                                });
                            }
                            
                            assignCount++;
                            break;
                        case 3:
                            if (_HiddenItemsCountLimit > -1) {
                                _Logger.log(Level.INFO, "Limiting number of hidden items at pos #{0}", index);
                                
                                iter.insertAt(index, new byte[] {
                                    0x11, // sipush
                                    (byte)(_HiddenItemsCountLimit >> 8),
                                    (byte)(_HiddenItemsCountLimit & 0xFF),
                                    (byte)0xB8, // invokestatic
                                    (byte)(mathMinIndex >> 8),
                                    (byte)(mathMinIndex & 0xFF)
                                });
                            }
                            
                            assignCount++;
                            break;
                        default:
                            _Logger.log(Level.WARNING, "While applying limits for the sacrifice item count, more than 4 variable assignments for 'required' were found. The mod requires an update or a compatability patch with another mod, and is possibly broken.");
                            break;
                    }
                }
                
                if (_HiddenItemsCountLimit > -1 && iter.byteAt(index) == Opcode.ISTORE && iter.byteAt(index + 1) == numsIndex) {
                    _Logger.log(Level.INFO, "Limiting modified number of required hidden items at pos #{0}.", index);
                    
                    iter.insertAt(index, new byte[] { 
                        0x10, // bipush
                        (byte)(_HiddenItemsCountLimit & 0xFF),
                        (byte)0xB8, // invokestatic
                        (byte)(mathMinIndex >> 8), // msb of the java.lang.Math.min() index in the constant pool.
                        (byte)(mathMaxIndex & 0xFF) // lsb
                    });
                }
            }
        }
        
        if (!_UseGiveMissions) {
            _Logger.info("Disabling generation of give (item to avatar) missions.");
            ctClass.getMethod("createGiveMission", "(Lcom/wurmonline/server/tutorial/Mission;BILjava/lang/String;II)Z").setBody("{ return false; }");
        }
        else if (_GiveCountLimit > -1) {
            _Logger.info("Limiting number of give missions.");
            
            ctClass.getMethod("createGiveMission", "(Lcom/wurmonline/server/tutorial/Mission;BILjava/lang/String;II)Z").instrument(
                new ExprEditor() { 
                    @Override
                    public void edit(MethodCall call) throws CannotCompileException {
                        // Replaces the only call (vanilla code) to respect our number limit.
                        if (call.getClassName().equals("com.wurmonline.server.epic.EpicServerStatus") && call.getMethodName().equals("getNumberOfRequiredPlayers")) {
                            _Logger.log(Level.INFO, "Found method call to limit give count limit at line #{0}", call.getLineNumber());
                            call.replace("{ $_ = Math.min( $proceed($$), " + _GiveCountLimit + "); }");
                        }
                    }
                }
            );
        }
    }
    
    /**
     * Makes traitor creatures show up when tracking.
     */
    protected void TraitorHint() {
        try {
            _Logger.log(Level.INFO, "Initializing traitor hints.");
            
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
                        _Logger.log(Level.INFO, "Method call for traitor hints found, applying mod code.");
                        
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
        } 
        catch (NotFoundException | CannotCompileException ex) {
            _Logger.info("Traitor hints could not be applied.");
            _Logger.log(Level.SEVERE, null, ex);
        }
        
    }
    
    /**
    * Makes trees that are to be cut down centered on tile and, pre-set them to 69 damage.
    */
    protected void TreeHint() {
        try {
            _Logger.log(Level.INFO, "Initialising epic mission cut tree hint.");
            
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
                    if (methodCall.getMethodName().equals("getTileName")) {
                        _Logger.info("Method call for tree hint found, applying mod code.");
                        ctMethod.insertAt(methodCall.getLineNumber(), "{ com.wurmonline.server.Server.setWorldResource(targTile.getTileX(), targTile.getTileY(), 69); com.wurmonline.server.Server.setSurfaceTile(targTile.getTileX(), targTile.getTileY(), com.wurmonline.mesh.Tiles.decodeHeight(com.wurmonline.server.Server.surfaceMesh.getTile(targTile.getTileX(), targTile.getTileY())), com.wurmonline.mesh.Tiles.decodeType(com.wurmonline.server.Server.surfaceMesh.getTile(targTile.getTileX(), targTile.getTileY())), com.wurmonline.mesh.Tiles.encodeTreeData(com.wurmonline.mesh.FoliageAge.OLD_ONE, false, true, com.wurmonline.mesh.GrassData.GrowthTreeStage.SHORT)); } ");
                    }
                }
            });
        }
        catch (CannotCompileException | NotFoundException ex) {
            _Logger.log(Level.SEVERE, "Tree hints could not be applied.");
            _Logger.log(Level.SEVERE, null, ex);
        }
    }
}
