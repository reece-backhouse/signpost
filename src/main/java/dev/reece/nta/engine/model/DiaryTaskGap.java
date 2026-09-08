package dev.reece.nta.engine.model;

import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * One incomplete task within a diary tier goal (ruling 16: the goal is the tier, tasks are gap
 * nodes). {@code gaps} are the task's own unmet skill/quest/item/combat requirements; {@code notes}
 * are display-only text carried from the knowledge base (e.g. non-evaluated kudos requirements)
 * plus a note for any quest requirement the knowledge base couldn't resolve by name.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class DiaryTaskGap extends Gap
{
	int ordinal;
	String text;
	List<Gap> gaps;
	List<String> notes;
}
