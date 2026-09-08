package dev.reece.nta.snapshot;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Mirrors the account type values carried by {@code VarbitID.IRONMAN} (varbit 1777).
 */
@Slf4j
@Getter
public enum AccountType
{
	NORMAL(0),
	IRONMAN(1),
	ULTIMATE(2),
	HARDCORE(3),
	GROUP(4),
	HARDCORE_GROUP(5),
	UNRANKED_GROUP(6);

	private final int id;

	AccountType(int id)
	{
		this.id = id;
	}

	/**
	 * True for every iron variant (ids 1..6): ironman, ultimate, hardcore, and the three group ironman flavours.
	 */
	public boolean isIron()
	{
		return id >= 1 && id <= 6;
	}

	public static AccountType fromVarbit(int value)
	{
		for (AccountType type : values())
		{
			if (type.id == value)
			{
				return type;
			}
		}

		log.warn("Unknown account type varbit value {}, defaulting to NORMAL", value);
		return NORMAL;
	}
}
