package net.ripe.rpki.rtr.domain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
@Slf4j
public class RpkiCache {
    public static final Long MAX_SERIAL_NUMBER = ((1L << 32) - 1);

    private long serialNumber = 0;
    private Set<RoaPrefix> roaPrefixes = new LinkedHashSet<>();

    public synchronized void updateValidatedRoas(Collection<RoaPrefix> roaPrefixes) {
        Set<RoaPrefix> updated = new LinkedHashSet<>(roaPrefixes);
        if (this.roaPrefixes.equals(updated)) {
            log.info("no updates to validated ROA prefixes");
            return;
        }

        serialNumber = (serialNumber + 1) & MAX_SERIAL_NUMBER;
        this.roaPrefixes = new LinkedHashSet<>(roaPrefixes);
        log.info("{} validated ROAs updated to serial number {}", roaPrefixes.size(), serialNumber);
    }
}
