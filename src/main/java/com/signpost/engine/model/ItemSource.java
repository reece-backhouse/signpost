package com.signpost.engine.model;

import lombok.Value;

/**
 * Where a missing item could be obtained, as described by the knowledge base.
 */
@Value
public class ItemSource
{
	String type;
	String where;
	String detail;
}
