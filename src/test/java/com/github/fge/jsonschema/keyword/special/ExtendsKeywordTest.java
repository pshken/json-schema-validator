/*
 * Copyright (c) 2013, Francis Galiegue <fgaliegue@gmail.com>
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

package com.github.fge.jsonschema.keyword.special;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jackson.JacksonUtils;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jsonschema.exceptions.ProcessingException;
import com.github.fge.jsonschema.keyword.validator.KeywordValidator;
import com.github.fge.jsonschema.library.ValidationMessageBundle;
import com.github.fge.jsonschema.library.validator.DraftV3ValidatorDictionary;
import com.github.fge.jsonschema.processing.Processor;
import com.github.fge.jsonschema.processors.data.FullData;
import com.github.fge.jsonschema.report.ProcessingMessage;
import com.github.fge.jsonschema.report.ProcessingReport;
import com.github.fge.jsonschema.tree.CanonicalSchemaTree;
import com.github.fge.jsonschema.tree.JsonTree;
import com.github.fge.jsonschema.tree.SchemaTree;
import com.github.fge.jsonschema.tree.SimpleJsonTree;
import com.github.fge.msgsimple.bundle.MessageBundle;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static com.github.fge.jsonschema.TestUtils.*;
import static com.github.fge.jsonschema.matchers.ProcessingMessageAssert.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public final class ExtendsKeywordTest
{
    private static final MessageBundle BUNDLE = ValidationMessageBundle.get();
    private static final String FOO = "foo";
    private static final JsonNodeFactory FACTORY = JacksonUtils.nodeFactory();

    private final KeywordValidator validator;

    private Processor<FullData, FullData> processor;
    private FullData data;
    private ProcessingReport report;
    private ProcessingMessage msg;

    public ExtendsKeywordTest()
        throws IllegalAccessException, InvocationTargetException,
        InstantiationException
    {
        final Constructor<? extends KeywordValidator> constructor
            = DraftV3ValidatorDictionary.get().entries().get("extends");
        validator = constructor == null ? null
            : constructor.newInstance(FACTORY.nullNode());
    }

    @BeforeMethod
    public void initEnvironment()
    {
        if (validator == null)
            return;

        final ObjectNode schema = FACTORY.objectNode();
        schema.put("extends", FACTORY.objectNode());
        final SchemaTree tree = new CanonicalSchemaTree(schema);

        final JsonTree instance = new SimpleJsonTree(FACTORY.nullNode());
        data = new FullData(tree, instance);

        report = mock(ProcessingReport.class);
        msg = new ProcessingMessage().message(FOO);
    }

    @Test
    public void keywordExists()
    {
        assertNotNull(validator, "no support for extends??");
    }

    @Test(dependsOnMethods = "keywordExists")
    public void exceptionIsCorrectlyThrown()
    {
        processor = new DummyProcessor(WantedState.EX, msg);

        try {
            validator.validate(processor, report, BUNDLE, data);
            fail("No exception thrown??");
        } catch (ProcessingException ignored) {
        }
    }

    @Test(dependsOnMethods = "keywordExists")
    public void failingSubSchemaLeadsToFailure()
        throws ProcessingException
    {
        final ArgumentCaptor<ProcessingMessage> captor
            = ArgumentCaptor.forClass(ProcessingMessage.class);

        processor = new DummyProcessor(WantedState.KO, msg);

        validator.validate(processor, report, BUNDLE, data);

        verify(report).error(captor.capture());

        final ProcessingMessage message = captor.getValue();

        assertMessage(message).hasMessage(FOO);
    }

    @Test(dependsOnMethods = "keywordExists")
    public void successfulSubSchemaLeadsToSuccess()
        throws ProcessingException
    {
        processor = new DummyProcessor(WantedState.OK, msg);

        validator.validate(processor, report, BUNDLE, data);

        verify(report, never()).error(anyMessage());
    }

    private enum WantedState {
        OK
        {
            @Override
            void doIt(final ProcessingReport report,
                final ProcessingMessage message)
                throws ProcessingException
            {
            }
        },
        KO
        {
            @Override
            void doIt(final ProcessingReport report,
                final ProcessingMessage message)
                throws ProcessingException
            {
                report.error(message);
            }
        },
        EX
        {
            @Override
            void doIt(final ProcessingReport report,
                final ProcessingMessage message)
                throws ProcessingException
            {
                throw new ProcessingException();
            }
        };

        abstract void doIt(final ProcessingReport report,
            final ProcessingMessage message)
            throws ProcessingException;
    }

    private static final class DummyProcessor
        implements Processor<FullData, FullData>
    {
        private static final JsonPointer PTR = JsonPointer.of("extends");

        private final WantedState wanted;
        private final ProcessingMessage message;

        private DummyProcessor(final WantedState wanted,
            final ProcessingMessage message)
        {
            this.wanted = wanted;
            this.message = message;
        }

        @Override
        public FullData process(final ProcessingReport report,
            final FullData input)
            throws ProcessingException
        {
            assertEquals(input.getSchema().getPointer(), PTR);
            wanted.doIt(report, message);
            return input;
        }
    }
}
