/*******************************************************************************
 * Copyright (c) 2023 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package io.openliberty.data.repository;

/**
 * Functions for {@link Filter#fn()}.
 */
public enum Function {
    /**
     * <p>The {@code AbsoluteValue} {@link Filter#fn() function}
     * computes the value of numbers absent their sign.</p>
     *
     * <p>Applies to: numeric.</p>
     *
     * <p>Example query:</p>
     *
     * <pre>
     * {@literal @Filter}(by = "deviation", fn = Function.AbsoluteValue, Compare.LessThan)
     * List{@literal <Result>} within(float maxDeviation);
     * </pre>
     *
     * <p>Example usage:</p>
     *
     * <pre>
     * dataToUse = results.within(0.05f);
     * </pre>
     */
    AbsoluteValue(),

    /**
     * <p>The {@code CharCount} {@link Filter#fn() function}
     * computes the length of string values.</p>
     *
     * <p>Applies to: strings.</p>
     *
     * <p>Example query:</p>
     *
     * <pre>
     * {@literal @Filter}(by = "phoneNumber", fn = Function.CharCount, value = "10")
     * {@literal @Filter}(by = "timezone")
     * List{@literal <Customer>} withAreaCodeIn(String timeZoneId);
     * </pre>
     *
     * <p>Example usage:</p>
     *
     * <pre>
     * centralTimeCallList = customers.withAreaCodeIn("America/Chicago");
     * </pre>
     */
    CharCount(),

    /**
     * <p>The {@code ElementCount} {@link Filter#fn() function}
     * counts the number of elements in a collection values.</p>
     *
     * <p>Applies to: collections.</p>
     *
     * <p>Example query:</p>
     *
     * <pre>
     * {@literal @Exists}
     * {@literal @Filter}(by = "emailAddresses", fn = Function.ElementCount, op = Compare.GreaterThan, value = "0")
     * {@literal @Filter}(by = "customerId")
     * boolean hasEmailAddress(long customerId);
     * </pre>
     *
     * <p>Example usage:</p>
     *
     * <pre>
     * canReceiveEmail = customers.hasEmailAddress(customerId);
     * </pre>
     */
    ElementCount(),

    /**
     * <p>The {@code IgnoreCase} {@link Filter#fn() function}
     * requests case insensitive comparison from a database with case sensitive collation.
     * A database with case insensitive collation performs case insensitive ordering
     * regardless of <code>IgnoreCase</code>.</p>
     *
     * <p>Applies to: strings.</p>
     *
     * <p>Example query:</p>
     *
     * <pre>
     * {@literal @Filter}(by = "state", fn = Function.IgnoreCase)
     * List{@literal <Person>} fromState(String stateName);
     * </pre>
     *
     * <p>Example usage:</p>
     *
     * <pre>
     * minnesotans = people.fromState("Minnesota");
     * </pre>
     */
    IgnoreCase(),

    /**
     * <p>The {@code Rounded} {@link Filter#fn() function}
     * computes the nearest integer as a floating point value.</p>
     *
     * <p>Applies to: floating point numeric.</p>
     *
     * <p>Example query:</p>
     *
     * <pre>
     * {@literal @Filter}(by = "weight", fn = Function.Rounded)
     * List{@literal <Product>} weighingAbout(float amount);
     * </pre>
     *
     * <p>Example usage:</p>
     *
     * <pre>
     * found = products.weighingAbout(15.0f);
     * </pre>
     */
    Rounded(),

    /**
     * <p>The {@code RoundedDown} {@link Filter#fn() function}
     * rounds down to the nearest integer as a floating point value.</p>
     *
     * <p>Applies to: floating point numeric.</p>
     *
     * <p>Example query:</p>
     *
     * <pre>
     * {@literal @Filter}(by = "price", fn = Function.RoundedDown, op = Compare.Between)
     * List{@literal <Product>} costingWithin(float minCost, float maxCost);
     * </pre>
     *
     * <p>Example usage:</p>
     *
     * <pre>
     * tenToTwentyDollarItems = products.costingWithin(10.0f, 20.0f);
     * </pre>
     */
    RoundedDown(),

    /**
     * <p>The {@code RoundedUp} {@link Filter#fn() function}
     * rounds up to the nearest integer as a floating point value.</p>
     *
     * <p>Applies to: floating point numeric.</p>
     *
     * <p>Example query:</p>
     *
     * <pre>
     * {@literal @Filter}(by = "length", op = Function.RoundedUp)
     * {@literal @Filter}(by = "width", op = Function.RoundedUp)
     * List{@literal <Package>} goodFitFor(float length, float width);
     * </pre>
     *
     * <p>Example usage:</p>
     *
     * <pre>
     * p = packages.goodFitFor(20.0f, 12.0f);
     * </pre>
     */
    RoundedUp(),

    /**
     * <p>The {@code Trimmed} {@link Filter#fn() function}
     * omits leading and trailing space characters from the value
     * when performing comparisons. When combining {@code Trimmed}
     * with a second function, order the {@code Trimmed} function
     * first.</p>
     *
     * <p>Applies to: strings.</p>
     *
     * <p>Example query:</p>
     *
     * <pre>
     * {@literal @Select}("name")
     * {@literal @Filter}(by = "name", fn = { Function.Trimmed, Function.CharCount })
     * {@literal @OrderBy}("name")
     * List{@literal <String>} namesOfLength(int nameLength);
     * </pre>
     *
     * <p>Example usage:</p>
     *
     * <pre>
     * fiveLetterNames = people.namesOfLength(5);
     * </pre>
     */
    Trimmed(),

    /**
     * <p>The {@code WithDay} {@link Filter#fn() function}
     * extracts the day of month field of date/time values from
     * the database. Note that the database might store values
     * in a different time zone than the application uses.</p>
     *
     * <p>Applies to: time.</p>
     *
     * <p>Example query:</p>
     *
     * <pre>
     * {@literal @Filter}(by = "birthdate", fn = Function.Month)
     * {@literal @Filter}(by = "birthdate", fn = Function.Day)
     * List{@literal <Person>} bornOn(int month, int day);
     * </pre>
     *
     * <p>Example usage:</p>
     *
     * <pre>
     * bornOnFeb29 = people.bornOn(Month.FEBRUARY.getValue(), 29);
     * </pre>
     */
    WithDay(),

    /**
     * <p>The {@code WithHour} {@link Filter#fn() function}
     * extracts the hour of the day of date/time values from
     * the database. Note that the database might store values
     * in a different time zone than the application uses.</p>
     *
     * <p>Applies to: time.</p>
     *
     * <p>Example query:</p>
     *
     * <pre>
     * {@literal @Filter}(by = "startTime", fn = Function.Hour)
     * List{@literal <Meeting>} startsAt(int hourOfDay);
     * </pre>
     *
     * <p>Example usage:</p>
     *
     * <pre>
     * earlyMeetings = meetings.startsAt(8);
     * </pre>
     */
    WithHour(),

    /**
     * <p>The {@code WithMinute} {@link Filter#fn() function}
     * extracts the minute of date/time values from
     * the database. Note that the database might store values
     * in a different time zone than the application uses.</p>
     *
     * <p>Applies to: time.</p>
     *
     * <p>Example query:</p>
     *
     * <pre>
     * {@literal @Filter}(by = "startTime", fn = Function.Minute, value = "15")
     * List{@literal <Meeting>} startingAtQuarterAfter();
     * </pre>
     *
     * <p>Example usage:</p>
     *
     * <pre>
     * m = meetings.startingAtQuarterAfter();
     * </pre>
     */
    WithMinute(),

    /**
     * <p>The {@code WithMonth} {@link Filter#fn() function}
     * extracts the month of date/time values.</p>
     *
     * <p>Applies to: time.</p>
     *
     * <p>Example query:</p>
     *
     * <pre>
     * {@literal @Count}
     * {@literal @Filter}(by = "birthdate", fn = Function.Month)
     * long numBirthdaysIn(int month);
     * </pre>
     *
     * <p>Example usage:</p>
     *
     * <pre>
     * numMayBirthdays = people.numBirthdaysIn(Month.MAY.getValue());
     * </pre>
     */
    WithMonth(),

    /**
     * <p>The {@code WithQuarter} {@link Filter#fn() function}
     * extracts the quarter of the year of date/time values from
     * the database. Note that the database might store values
     * in a different time zone than the application uses.</p>
     *
     * <p>Applies to: time.</p>
     *
     * <p>Example query:</p>
     *
     * <pre>
     * {@literal @Filter}(by = "date", fn = Function.Quarter)
     * {@literal @OrderBy}("year")
     * List{@literal <Result>} forQuarter(int quarter);
     * </pre>
     *
     * <p>Example usage:</p>
     *
     * <pre>
     * fourthQuarterResults = businessResults.forQuarter(4);
     * </pre>
     */
    WithQuarter(),

    /**
     * <p>The {@code WithSecond} {@link Filter#fn() function}
     * extracts the seconds field of date/time values.</p>
     *
     * <p>Applies to: time.</p>
     *
     * <p>Example query:</p>
     *
     * <pre>
     * {@literal @Filter}(by = "time", fn = Function.Seconds, op = Compare.Not, value = "0")
     * List{@literal <Result>} needsRounding();
     * </pre>
     *
     * <p>Example usage:</p>
     *
     * <pre>
     * entriesToUpdate = results.needsRounding();
     * </pre>
     */
    WithSecond(),

    /**
     * <p>The {@code WithWeek} {@link Filter#fn() function}
     * extracts the week of the year of date/time values.
     * The values are database-specific. Some databases might use
     * {@code 0} for the first day(s) of the year if not a full week.</p>
     *
     * <p>Applies to: time.</p>
     *
     * <p>Example query:</p>
     *
     * <pre>
     * {@literal @Filter}(by = "endingDate", fn = Function.Week, op = Compare.Between)
     * List{@literal <SalesResult>} forWeeks(int firstWeek, int lastWeek);
     * </pre>
     *
     * <p>Example usage:</p>
     *
     * <pre>
     * totals = salesTotals.forWeeks(6, 10);
     * </pre>
     */
    WithWeek(),

    /**
     * <p>The {@code WithYear} {@link Filter#fn() function}
     * extracts the year field of date/time values from
     * the database. Note that the database might store values
     * in a different time zone than the application uses.</p>
     *
     * <p>Applies to: time.</p>
     *
     * <p>Example query:</p>
     *
     * <pre>
     * {@literal @Filter}(by = "yearHired", fn = Function.Year)
     * List{@literal <Employee>} hiredIn(int year);
     * </pre>
     *
     * <p>Example usage:</p>
     *
     * <pre>
     * recentHires = employees.hiredIn(Year.now().getValue());
     * </pre>
     */
    WithYear();
}
