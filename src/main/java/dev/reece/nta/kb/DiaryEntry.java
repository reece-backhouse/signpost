package dev.reece.nta.kb;

import dev.reece.nta.snapshot.DiaryTier;
import java.util.List;
import lombok.Value;

/**
 * All tasks for one achievement diary tier (e.g. Varrock Easy).
 */
@Value
public class DiaryEntry
{
	DiaryTier tier;
	int tierVarbit;
	List<DiaryTask> tasks;
}
