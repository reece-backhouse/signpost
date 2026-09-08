package dev.reece.nta.store;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Snooze
{
	private Instant until;
	private String gapFingerprint;
}
