package com.signpost.kb;

import com.signpost.snapshot.Snapshot;
import lombok.Value;

/**
 * How a single diary task's completion is tracked in-game: either a bit of a varp (most tasks -
 * bit {@code bit} of varp {@code varp}, RuneLite's {@code VarplayerRequirement(varp, false, bit)}
 * semantics: {@code (varpValue >> bit) & 1 == 1}) or a varbit that counts up to a threshold
 * (Karamja diary tasks - varbit {@code varbit} is "done" once its value reaches {@code doneMin}).
 * Exactly one of the two pairs is populated.
 */
@Value
public class TaskCompletion
{
	Integer varp;
	Integer bit;
	Integer varbit;
	Integer doneMin;

	public boolean isComplete(Snapshot snapshot)
	{
		if (varp != null)
		{
			int value = snapshot.getDiaryVarps().getOrDefault(varp, 0);
			return ((value >> bit) & 1) == 1;
		}
		int value = snapshot.getKaramjaVarbits().getOrDefault(varbit, 0);
		return value >= doneMin;
	}
}
