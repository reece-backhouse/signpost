package dev.reece.nta.engine.model;

import dev.reece.nta.kb.MethodEntry;
import java.util.List;
import lombok.Value;

/**
 * What's missing to close a {@link Route}'s {@code uncoveredXp} (ticket C7): the fastest method at
 * the level the route stopped at, and the material shortfall for it. {@code method} is {@code null}
 * and {@code items} empty when the route already covered the full xp delta. {@code xpShort} is the
 * route's uncovered xp and {@code actionsNeeded} how many {@code method} actions close it, so the
 * UI can print "~403,000 xp: Prayer potion(3) ×4,600". {@code alternative} (spec ruling 30) is the
 * best method whose materials are all obtainable, resolved the same way, set only when the primary
 * method's own materials are not all obtainable and some other candidate's are; its own
 * {@code alternative} is always {@code null}. {@code unobtainable} (task 61) names the materials
 * with no known source - each craft chain's leaf, not the intermediate it makes - so the UI can
 * say why the alternative is on offer; empty when every material is obtainable.
 */
@Value
public class Shortfall
{
	MethodEntry method;
	List<ShortfallItem> items;
	long xpShort;
	long actionsNeeded;
	Shortfall alternative;
	List<String> unobtainable;

	public Shortfall(MethodEntry method, List<ShortfallItem> items)
	{
		this(method, items, 0, 0, null);
	}

	public Shortfall(MethodEntry method, List<ShortfallItem> items, long xpShort, long actionsNeeded)
	{
		this(method, items, xpShort, actionsNeeded, null);
	}

	public Shortfall(MethodEntry method, List<ShortfallItem> items, long xpShort, long actionsNeeded, Shortfall alternative)
	{
		this(method, items, xpShort, actionsNeeded, alternative, List.of());
	}

	public Shortfall(MethodEntry method, List<ShortfallItem> items, long xpShort, long actionsNeeded, Shortfall alternative,
		List<String> unobtainable)
	{
		this.method = method;
		this.items = items;
		this.xpShort = xpShort;
		this.actionsNeeded = actionsNeeded;
		this.alternative = alternative;
		this.unobtainable = unobtainable;
	}
}
