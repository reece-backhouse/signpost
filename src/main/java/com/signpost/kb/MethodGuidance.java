package com.signpost.kb;

import java.util.List;
import java.util.Map;
import lombok.Value;

@Value
public class MethodGuidance
{
	public static final MethodGuidance DEFAULT = new MethodGuidance("any bank", List.of(), Map.of());
	String location;
	List<BringItem> bring;
	Map<String, String> locations;

	public String locationFor(MethodEntry method)
	{
		return locations.getOrDefault(method.getSkill().name() + ":" + method.getTitle(), location);
	}
}
