package com.google.common.collect;

import java.util.Map;

/**
 * Redeclares the package-private members of ImmutableMap as public, so it can be extended in other packages
 */
public abstract class FerriteCoreImmutableMapAccess<K, V> extends ImmutableMap<K, V> {

    public FerriteCoreImmutableMapAccess() {}

    @Override
    public abstract ImmutableSet<Map.Entry<K, V>> createEntrySet();

    @Override
    public abstract boolean isPartialView();

    @Override
    public abstract ImmutableSet<K> createKeySet();

    //#if MC==11202
    @Override
    public abstract ImmutableCollection<V> createValues();
    //#else
    //$$ private transient ImmutableCollection<V> values;
    //$$
    //$$ /**
    //$$  * Returns an immutable collection of the values in this map. The values are
    //$$  * in the same order as the parameters used to build this map.
    //$$  */
    //$$ @Override
    //$$ @org.jetbrains.annotations.NotNull
    //$$ public ImmutableCollection<V> values() {
    //$$     ImmutableCollection<V> result = values;
    //$$     return (result == null) ? values = createValues() : result;
    //$$ }
    //$$
    //$$ public abstract ImmutableCollection<V> createValues();
    //#endif
}
