package com.signpost.kb;

import com.signpost.snapshot.PrayerUnlock;
import java.util.List;
import lombok.Value;

/** Curated readiness: every gear role is required, with interchangeable alternatives within a role. */
@Value
public class RecommendedProfile
{
	List<RecommendedSkill> skills;
	Integer combatLevel;
	List<GearRequirement> gear;
	List<PrayerUnlock> prayers;
}
