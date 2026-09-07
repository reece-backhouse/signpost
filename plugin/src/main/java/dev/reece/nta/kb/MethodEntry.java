package dev.reece.nta.kb;

import java.util.List;
import lombok.Value;
import net.runelite.api.Skill;

/**
 * One training method from {@code methods.json} (a Bucket-scraped action: "Prayer potion(3)",
 * "Chop willow trees", ...). {@code intermediate} methods (e.g. unfinished potions) grant 0 xp and
 * exist only so {@link dev.reece.nta.engine.RoutePlanner} and
 * {@link dev.reece.nta.engine.ShortfallResolver} can recurse one level into them when a real
 * method's material is itself craftable. {@code usable} is false when any material or output name
 * didn't resolve to a known {@code materials.json} entry (id stays {@code null} on that
 * {@link ItemQuantity}); such a method is kept for display but never a route candidate.
 */
@Value
public class MethodEntry
{
	Skill skill;
	String name;
	String title;
	int levelReq;
	double xpPerAction;
	List<ItemQuantity> materials;
	List<ItemQuantity> outputs;
	List<String> types;
	boolean members;
	boolean boostable;
	Integer ticks;
	boolean intermediate;
	boolean usable;
}
