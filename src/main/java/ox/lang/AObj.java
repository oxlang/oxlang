package ox.lang;

import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Created by arrdem on 10/5/15.
 */
public abstract class AObj implements IMeta {
    @Override
    @NotNull
    public abstract String toString();

    @Override
    public abstract boolean equals(Object other);
}