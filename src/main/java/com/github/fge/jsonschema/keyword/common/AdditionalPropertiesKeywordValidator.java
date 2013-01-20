/*
 * Copyright (c) 2012, Francis Galiegue <fgaliegue@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Lesser GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Lesser GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.fge.jsonschema.keyword.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.github.fge.jsonschema.keyword.KeywordValidator;
import com.github.fge.jsonschema.report.Message;
import com.github.fge.jsonschema.report.ValidationReport;
import com.github.fge.jsonschema.util.NodeType;
import com.github.fge.jsonschema.util.RhinoHelper;
import com.github.fge.jsonschema.validator.ValidationContext;

import java.util.Collections;
import java.util.Set;

/**
 * Validator for {@code additionalProperties}
 *
 * <p>Note that this keyword only handles validation at the instance level: it
 * does not validate children.</p>
 *
 * <p>The rules are:</p>
 *
 * <ul>
 *     <li>if {@code additionalProperties} is a schema or {@code true},
 *     validation succeeds;</li>
 *     <li>if it is {@code false}, then validation succeeds if and only if all
 *     instance members are either members in {@code properties} or match at
 *     least one regex of {@code patternProperties}.</li>
 *     </li>
 * </ul>
 */
public final class AdditionalPropertiesKeywordValidator
    extends KeywordValidator
{
    private static final Joiner TOSTRING_JOINER = Joiner.on("; or ");

    private final boolean additionalOK;
    private final Set<String> properties;
    private final Set<String> patternProperties;

    public AdditionalPropertiesKeywordValidator(final JsonNode schema)
    {
        super("additionalProperties", NodeType.OBJECT);
        additionalOK = schema.get(keyword).asBoolean(true);

        if (additionalOK) {
            properties = Collections.emptySet();
            patternProperties = Collections.emptySet();
            return;
        }

        properties = ImmutableSet.copyOf(schema.path("properties")
            .fieldNames());
        patternProperties = ImmutableSet.copyOf(schema.path("patternProperties")
            .fieldNames());
    }

    @Override
    public void validate(final ValidationContext context,
        final ValidationReport report, final JsonNode instance)
    {
        final Set<String> fields = Sets.newHashSet(instance.fieldNames());

        fields.removeAll(properties);

        final Set<String> tmp = Sets.newHashSet();

        for (final String field: fields)
            for (final String regex: patternProperties)
                if (RhinoHelper.regMatch(regex, field))
                    tmp.add(field);

        fields.removeAll(tmp);

        if (fields.isEmpty())
            return;

        /*
         * Display extra properties in order in the report
         */
        final Message.Builder msg = newMsg()
            .addInfo("unwanted", Ordering.natural().sortedCopy(fields))
            .setMessage("additional properties not permitted");
        report.addMessage(msg.build());
    }

    @Override
    public boolean alwaysTrue()
    {
        return additionalOK;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder(keyword + ": ");

        if (additionalOK)
            return sb.append("allowed").toString();

        sb.append("none");

        if (properties.isEmpty() && patternProperties.isEmpty())
            return sb.toString();

        sb.append(", unless: ");

        final Set<String> further = Sets.newLinkedHashSet();

        if (!properties.isEmpty())
            further.add("one property is any of: " + properties);

        if (!patternProperties.isEmpty())
            further.add("a property matches any regex among: "
                + patternProperties);

        sb.append(TOSTRING_JOINER.join(further));

        return sb.toString();
    }
}