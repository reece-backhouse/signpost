package com.signpost.engine;

import java.util.List;
import lombok.Value;

/**
 * One diary tier's completion progress, as computed by {@link DiaryProgress}.
 */
@Value
public class DiaryTierProgress
{
	int completed;
	int total;
	List<Integer> completedOrdinals;
	Integer gameCount;
	boolean mismatch;
}
