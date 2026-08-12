package com.gnottero.blossom.api;

import com.gnottero.blossom.ScriptContext;
import blossom.interpreter.Expression;
import blossom.exception.InternalExpressionException;
import com.gnottero.blossom.access.Vanilla;
import com.gnottero.blossom.util.SystemInfo;
import blossom.value.ListValue;
import blossom.value.MapValue;
import blossom.value.NumericValue;
import blossom.value.StringValue;
import blossom.value.Value;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;

public class Monitoring
{
    private static final Map<String, MobCategory> MOB_CATEGORY_MAP = Arrays.stream(MobCategory.values()).collect(Collectors.toMap(MobCategory::getName, Function.identity()));

    public static void apply(Expression expression)
    {
        expression.addContextFunction("system_info", -1, (c, t, lv) ->
        {
            if (lv.isEmpty())
            {
                return SystemInfo.getAll();
            }
            if (lv.size() == 1)
            {
                String what = lv.get(0).getString();
                Value res = SystemInfo.get(what, (ScriptContext) c);
                if (res == null)
                {
                    throw new InternalExpressionException("Unknown option for 'system_info': " + what);
                }
                return res;
            }
            throw new InternalExpressionException("'system_info' requires one or no parameters");
        });
        // game processed snooper functions
        expression.addContextFunction("get_mob_counts", -1, (c, t, lv) ->
        {
            ScriptContext cc = (ScriptContext) c;
            ServerLevel world = cc.level();
            NaturalSpawner.SpawnState info = world.getChunkSource().getLastSpawnState();
            if (info == null)
            {
                return Value.NULL;
            }
            Object2IntMap<MobCategory> mobcounts = info.getMobCategoryCounts();
            int chunks = info.getSpawnableChunkCount();
            if (lv.isEmpty())
            {
                Map<Value, Value> retDict = new HashMap<>();
                for (MobCategory category : mobcounts.keySet())
                {
                    int currentCap = category.getMaxInstancesPerChunk() * chunks / Vanilla.NaturalSpawner_MAGIC_NUMBER();
                    retDict.put(
                            new StringValue(category.getSerializedName().toLowerCase(Locale.ROOT)),
                            ListValue.of(
                                    new NumericValue(mobcounts.getInt(category)),
                                    new NumericValue(currentCap))
                    );
                }
                return MapValue.wrap(retDict);
            }
            String catString = lv.get(0).getString();
            MobCategory cat = MOB_CATEGORY_MAP.get(catString.toLowerCase(Locale.ROOT));
            if (cat == null)
            {
                throw new InternalExpressionException("Unreconized mob category: " + catString);
            }
            return ListValue.of(
                    new NumericValue(mobcounts.getInt(cat)),
                    new NumericValue((long) cat.getMaxInstancesPerChunk() * chunks / Vanilla.NaturalSpawner_MAGIC_NUMBER())
            );
        });
    }
}
