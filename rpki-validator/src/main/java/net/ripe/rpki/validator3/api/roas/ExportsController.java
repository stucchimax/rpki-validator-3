/**
 * The BSD License
 *
 * Copyright (c) 2010-2018 RIPE NCC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the RIPE NCC nor the names of its contributors may be
 *     used to endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.ripe.rpki.validator3.api.roas;

import au.com.bytecode.opencsv.CSVWriter;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.PublicApiCall;
import net.ripe.rpki.validator3.domain.validation.ValidatedRpkiObjects;
import net.ripe.rpki.validator3.storage.Storage;
import net.ripe.rpki.validator3.storage.stores.Settings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Stream;

import static net.ripe.rpki.validator3.api.ModelPropertyDescriptions.*;

/**
 * Controller to export validated ROA prefix information.
 * <p>
 * The API and data format is backwards compatible with the RPKI validator 2.x (see
 * https://github.com/RIPE-NCC/rpki-validator/blob/350d939d5e18858ee6cefc0c9a99e0c70b609b6d/rpki-validator-app/src/main/scala/net/ripe/rpki/validator/controllers/ExportController.scala#L41).
 */
@Api(tags = "VRP export")
@PublicApiCall
@RestController
@Slf4j
public class ExportsController {

    public static final String JSON = "text/json; charset=UTF-8";
    public static final String CSV = "text/csv; charset=UTF-8";

    private final ValidatedRpkiObjects validatedRpkiObjects;

    private final Settings settings;
    private final Storage storage;

    @Autowired
    public ExportsController(ValidatedRpkiObjects validatedRpkiObjects, Settings settings, Storage storage) {
        this.validatedRpkiObjects = validatedRpkiObjects;
        this.settings = settings;
        this.storage = storage;
    }

    @ApiOperation("export VRPs (json)")
    @GetMapping(path = "/api/export.json")
    public JsonExport exportJson(HttpServletResponse response) {
        response.setContentType(JSON);

        if (!storage.readTx(settings::isInitialValidationRunCompleted)) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return null;
        }

        Stream<JsonRoaPrefix> validatedPrefixes = validatedRpkiObjects
                .findCurrentlyValidatedRoaPrefixes()
                .getObjects()
                .map(r -> new JsonRoaPrefix(
                        String.valueOf(r.getAsn()),
                        r.getPrefix().toString(),
                        r.getEffectiveLength(),
                        r.getTrustAnchor().getName()
                ))
                .distinct();

        return new JsonExport(validatedPrefixes);
    }

    @ApiOperation("export VRPs (CSV)")
    @GetMapping(path = "/api/export.csv")
    public void exportCsv(HttpServletResponse response) throws IOException {
        response.setContentType(CSV);

        if (!storage.readTx(settings::isInitialValidationRunCompleted)) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        try (CSVWriter writer = new CSVWriter(response.getWriter())) {
            writer.writeNext(new String[]{"ASN", "IP Prefix", "Max Length", "Trust Anchor"});
            Stream<CsvRoaPrefix> validatedPrefixes = validatedRpkiObjects
                    .findCurrentlyValidatedRoaPrefixes()
                    .getObjects()
                    .map(r -> new CsvRoaPrefix(
                            String.valueOf(r.getAsn()),
                            r.getPrefix().toString(),
                            r.getEffectiveLength(),
                            r.getTrustAnchor().getName())
                    )
                    .distinct();
            validatedPrefixes.forEach(prefix -> {
                writer.writeNext(new String[]{
                        prefix.getAsn(),
                        prefix.getPrefix(),
                        String.valueOf(prefix.getMaxLength()),
                        prefix.getTrustAnchorName()
                });
            });
        }
    }

    @Value
    private static class CsvRoaPrefix {
        private String asn;
        private String prefix;
        private int maxLength;
        private String trustAnchorName;
    }

    @Value
    private static class JsonExport {
        @ApiModelProperty(position = 3)
        Stream<JsonRoaPrefix> roas;
    }

    @Value
    private static class JsonRoaPrefix {
        @ApiModelProperty(value = ASN_PROPERTY, example = ASN_EXAMPLE)
        private String asn;
        @ApiModelProperty(example = PREFIX_EXAMPLE)
        private String prefix;
        private int maxLength;
        @ApiModelProperty(value = TRUST_ANCHOR, example = TRUST_ANCHOR_EXAMPLE)
        private String ta;
    }
}
