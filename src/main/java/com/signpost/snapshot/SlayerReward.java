package com.signpost.snapshot;

import lombok.Getter;
import net.runelite.api.gameval.VarbitID;

@Getter
public enum SlayerReward
{
	MALEVOLENT_MASQUERADE("Malevolent masquerade", VarbitID.SLAYER_HELM_UNLOCKED),
	RING_BLING("Ring bling", VarbitID.SLAYER_RING_UNLOCKED),
	BROADER_FLETCHING("Broader fletching", VarbitID.SLAYER_AMMO_UNLOCKED),
	LIKE_A_BOSS("Like a boss", VarbitID.SLAYER_UNLOCK_BOSSES),
	BIGGER_AND_BADDER("Bigger and Badder", VarbitID.SLAYER_UNLOCK_SUPERIORMOBS),
	LONGER_DARKBEASTS("Extended darkbeasts", VarbitID.SLAYER_LONGER_DARKBEASTS),
	LONGER_ANKOU("Extended ankou", VarbitID.SLAYER_LONGER_ANKOU),
	LONGER_SUQAH("Extended suqah", VarbitID.SLAYER_LONGER_SUQAH),
	LONGER_BLACKDRAGONS("Extended blackdragons", VarbitID.SLAYER_LONGER_BLACKDRAGONS),
	LONGER_METALDRAGONS("Extended metaldragons", VarbitID.SLAYER_LONGER_METALDRAGONS),
	LONGER_ABYSSALDEMONS("Extended abyssaldemons", VarbitID.SLAYER_LONGER_ABYSSALDEMONS),
	LONGER_BLACKDEMONS("Extended blackdemons", VarbitID.SLAYER_LONGER_BLACKDEMONS),
	LONGER_GREATERDEMONS("Extended greaterdemons", VarbitID.SLAYER_LONGER_GREATERDEMONS),
	LONGER_BLOODVELD("Extended bloodveld", VarbitID.SLAYER_LONGER_BLOODVELD),
	LONGER_ABERRANTSPECTRES("Extended aberrantspectres", VarbitID.SLAYER_LONGER_ABERRANTSPECTRES),
	LONGER_AVIANSIES("Extended aviansies", VarbitID.SLAYER_LONGER_AVIANSIES),
	LONGER_MITHRILDRAGONS("Extended mithrildragons", VarbitID.SLAYER_LONGER_MITHRILDRAGONS),
	LONGER_CAVEHORRORS("Extended cavehorrors", VarbitID.SLAYER_LONGER_CAVEHORRORS),
	LONGER_DUSTDEVILS("Extended dustdevils", VarbitID.SLAYER_LONGER_DUSTDEVILS),
	LONGER_SKELETALWYVERNS("Extended skeletalwyverns", VarbitID.SLAYER_LONGER_SKELETALWYVERNS),
	LONGER_GARGOYLES("Extended gargoyles", VarbitID.SLAYER_LONGER_GARGOYLES),
	LONGER_NECHRYAEL("Extended nechryael", VarbitID.SLAYER_LONGER_NECHRYAEL),
	LONGER_CAVEKRAKEN("Extended cavekraken", VarbitID.SLAYER_LONGER_CAVEKRAKEN),
	LONGER_SPIRITUALGWD("Extended spiritualgwd", VarbitID.SLAYER_LONGER_SPIRITUALGWD),
	LONGER_SCABARITES("Extended scabarites", VarbitID.SLAYER_LONGER_SCABARITES),
	LONGER_FOSSILWYVERNS("Extended fossilwyverns", VarbitID.SLAYER_LONGER_FOSSILWYVERNS),
	LONGER_ADAMANTDRAGONS("Extended adamantdragons", VarbitID.SLAYER_LONGER_ADAMANTDRAGONS),
	LONGER_RUNEDRAGONS("Extended runedragons", VarbitID.SLAYER_LONGER_RUNEDRAGONS),
	LONGER_BASILISK("Extended basilisk", VarbitID.SLAYER_LONGER_BASILISK),
	LONGER_VAMPYRES("Extended vampyres", VarbitID.SLAYER_LONGER_VAMPYRES),
	LONGER_ARAXYTES("Extended araxytes", VarbitID.SLAYER_LONGER_ARAXYTES),
	LONGER_REVENANTS("Extended revenants", VarbitID.SLAYER_LONGER_REVENANTS),
	LONGER_CUSTODIANS("Extended custodians", VarbitID.SLAYER_LONGER_CUSTODIANS),
	LONGER_WYRMS("Extended wyrms", VarbitID.SLAYER_LONGER_WYRMS),
	LONGER_AQUANITES("Extended aquanites", VarbitID.SLAYER_LONGER_AQUANITES);

	private final String displayName;
	private final int varbitId;

	SlayerReward(String displayName, int varbitId)
	{
		this.displayName = displayName;
		this.varbitId = varbitId;
	}
}
