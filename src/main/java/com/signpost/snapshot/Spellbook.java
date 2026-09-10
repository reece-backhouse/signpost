package com.signpost.snapshot;

public enum Spellbook
{
	STANDARD, ANCIENT, LUNAR, ARCEUUS, UNKNOWN;

	public static Spellbook fromVarbit(int value)
	{
		switch (value)
		{
			case 0: return STANDARD;
			case 1: return ANCIENT;
			case 2: return LUNAR;
			case 3: return ARCEUUS;
			default: return UNKNOWN;
		}
	}
}
