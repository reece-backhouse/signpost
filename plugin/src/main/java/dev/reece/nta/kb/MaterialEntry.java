package dev.reece.nta.kb;

import dev.reece.nta.engine.model.ItemSource;
import java.util.List;
import lombok.Value;

/**
 * One item from {@code materials.json}: where it can be obtained. {@code id} is {@code null} for a
 * handful of entries the kb-build scrape couldn't resolve to an OSRS item id; such a material is
 * never a resolved {@link ItemQuantity#getId()} on any {@link MethodEntry}. Every bundled source's
 * {@code type == "GE"} exactly when its json {@code accountTypes} is {@code ["main"]} (verified at
 * build time), so {@link ItemSource} doesn't carry {@code accountTypes} at all -
 * {@link dev.reece.nta.engine.GapEngine#sourcesFor} filtering on {@code type == "GE"} already does
 * the same job for iron accounts.
 */
@Value
public class MaterialEntry
{
	String name;
	Integer id;
	boolean generic;
	List<ItemSource> sources;
}
