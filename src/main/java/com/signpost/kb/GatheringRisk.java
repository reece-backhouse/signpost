package com.signpost.kb;

import com.signpost.snapshot.AccountType;

public enum GatheringRisk
{
	NONE("none"), WILDERNESS("wilderness"), HARDCORE_UNSAFE("hardcore-unsafe");

	private final String label;

	GatheringRisk(String label)
	{
		this.label = label;
	}

	public String getLabel()
	{
		return label;
	}

	public boolean allowed(AccountType account, boolean includeWilderness)
	{
		boolean hardcore = account == AccountType.HARDCORE || account == AccountType.HARDCORE_GROUP;
		return this == NONE || (this == WILDERNESS ? includeWilderness : !hardcore);
	}

	public static GatheringRisk parse(String value, String context)
	{
		if (value == null)
		{
			return NONE;
		}
		for (GatheringRisk risk : values())
		{
			if (risk.label.equals(value))
			{
				return risk;
			}
		}
		throw new IllegalStateException(context + " has unknown risk: " + value);
	}
}
