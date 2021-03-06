/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.sql.expression.function.scalar.datetime;

import org.elasticsearch.xpack.sql.expression.Expression;
import org.elasticsearch.xpack.sql.expression.Nullability;
import org.elasticsearch.xpack.sql.expression.function.scalar.BinaryScalarFunction;
import org.elasticsearch.xpack.sql.expression.function.scalar.datetime.DateTimeProcessor.DateTimeExtractor;
import org.elasticsearch.xpack.sql.expression.gen.pipeline.Pipe;
import org.elasticsearch.xpack.sql.tree.NodeInfo;
import org.elasticsearch.xpack.sql.tree.Source;
import org.elasticsearch.xpack.sql.type.DataType;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.elasticsearch.xpack.sql.expression.function.scalar.datetime.NonIsoDateTimeProcessor.NonIsoDateTimeExtractor;

public class DatePart extends BinaryDateTimeFunction {

    public enum Part implements DateTimeField {
        YEAR(DateTimeExtractor.YEAR::extract, "years", "yyyy", "yy"),
        QUARTER(QuarterProcessor::quarter, "quarters", "qq", "q"),
        MONTH(DateTimeExtractor.MONTH_OF_YEAR::extract, "months", "mm", "m"),
        DAYOFYEAR(DateTimeExtractor.DAY_OF_YEAR::extract, "dy", "y"),
        DAY(DateTimeExtractor.DAY_OF_MONTH::extract, "days", "dd", "d"),
        WEEK(NonIsoDateTimeExtractor.WEEK_OF_YEAR::extract, "weeks", "wk", "ww"),
        WEEKDAY(NonIsoDateTimeExtractor.DAY_OF_WEEK::extract, "weekdays", "dw"),
        HOUR(DateTimeExtractor.HOUR_OF_DAY::extract, "hours", "hh"),
        MINUTE(DateTimeExtractor.MINUTE_OF_HOUR::extract, "minutes", "mi", "n"),
        SECOND(DateTimeExtractor.SECOND_OF_MINUTE::extract, "seconds", "ss", "s"),
        MILLISECOND(dt -> dt.get(ChronoField.MILLI_OF_SECOND), "milliseconds", "ms"),
        MICROSECOND(dt -> dt.get(ChronoField.MICRO_OF_SECOND), "microseconds", "mcs"),
        NANOSECOND(ZonedDateTime::getNano, "nanoseconds", "ns"),
        TZOFFSET(dt -> dt.getOffset().getTotalSeconds() / 60, "tz");

        private static final Map<String, Part> NAME_TO_PART;
        private static final List<String> VALID_VALUES;

        static {
            NAME_TO_PART = DateTimeField.initializeResolutionMap(values());
            VALID_VALUES = DateTimeField.initializeValidValues(values());
        }

        private Function<ZonedDateTime, Integer> extractFunction;
        private Set<String> aliases;

        Part(Function<ZonedDateTime, Integer> extractFunction, String... aliases) {
            this.extractFunction = extractFunction;
            this.aliases = Set.of(aliases);
        }

        @Override
        public Iterable<String> aliases() {
            return aliases;
        }

        public static List<String> findSimilar(String match) {
            return DateTimeField.findSimilar(NAME_TO_PART.keySet(), match);
        }

        public static Part resolve(String truncateTo) {
            return DateTimeField.resolveMatch(NAME_TO_PART, truncateTo);
        }

        public Integer extract(ZonedDateTime dateTime) {
            return extractFunction.apply(dateTime);
        }
    }

    public DatePart(Source source, Expression truncateTo, Expression timestamp, ZoneId zoneId) {
        super(source, truncateTo, timestamp, zoneId);
    }

    @Override
    public DataType dataType() {
        return DataType.INTEGER;
    }

    @Override
    protected BinaryScalarFunction replaceChildren(Expression newTruncateTo, Expression newTimestamp) {
        return new DatePart(source(), newTruncateTo, newTimestamp, zoneId());
    }

    @Override
    protected NodeInfo<? extends Expression> info() {
        return NodeInfo.create(this, DatePart::new, left(), right(), zoneId());
    }

    @Override
    public Nullability nullable() {
        return Nullability.TRUE;
    }

    @Override
    protected String scriptMethodName() {
        return "datePart";
    }

    @Override
    public Object fold() {
        return DatePartProcessor.process(left().fold(), right().fold(), zoneId());
    }

    @Override
    protected Pipe createPipe(Pipe left, Pipe right, ZoneId zoneId) {
        return new DatePartPipe(source(), this, left, right, zoneId);
    }

    @Override
    protected boolean resolveDateTimeField(String dateTimeField) {
        return Part.resolve(dateTimeField) != null;
    }

    @Override
    protected List<String> findSimilarDateTimeFields(String dateTimeField) {
        return Part.findSimilar(dateTimeField);
    }

    @Override
    protected List<String> validDateTimeFieldValues() {
        return Part.VALID_VALUES;
    }
}
