package dev.reece.nta.engine.model;

import java.util.List;
import lombok.Value;

/**
 * A {@link Goal} together with everything currently blocking it, as computed by
 * {@link dev.reece.nta.engine.GapEngine}.
 */
@Value
public class GoalStatus
{
	Goal goal;
	List<Gap> gaps;
	/** True when {@code gaps} is empty, i.e. the goal could be done right now. */
	boolean ready;
	/** True when any {@link ItemGap} (including inside a {@link DiaryTaskGap}) has an unknown {@code have} because the bank hasn't been seen. */
	boolean bankUnknown;
}
