/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2014, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.envers.test.integration.customtype;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Calendar;
import java.util.TimeZone;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.type.CalendarType;
import org.hibernate.usertype.UserType;

/**
 * @author Sebastian Götz (s got goetz at inform dot ch)
 */
public class UTCCalendarType implements UserType {
	private static final TimeZone timeZone = TimeZone.getTimeZone( "UTC" );

	@Override
	public int[] sqlTypes() {
		return new int[] { Types.TIMESTAMP };
	}

	@Override
	public Class returnedClass() {
		return Calendar.class;
	}

	@Override
	public boolean equals(Object x, Object y) throws HibernateException {
		if ( x == y ) {
			return true;
		}
		if ( x == null || y == null ) {
			return false;
		}
		return x.equals( y );
	}

	@Override
	public int hashCode(Object x) throws HibernateException {
		return x.hashCode();
	}

	@Override
	public Object nullSafeGet(ResultSet rs, String[] names, SessionImplementor session, Object owner) throws HibernateException, SQLException {
		/*
		 * Delegate the nullSafeGet to the Hibernate CalendarType since we ensured upon insert that the value is in UTC.
		 *
		 * ATTENTION: Hibernate CalendarType will read the date value into a Calendar instance generated by
		 * Calendar.getInstance(). So the resulting calendar will have the system's default time zone but the value
		 * actually is in UTC since we corrected it upon insertion already. So we just have to ensure, that the time
		 * zone of the calendar is being set to UTC. Otherwise when it comes to insertion/update again we would subtract
		 * the offset again which would lead to incorrect values.
		 */
		Calendar calendar = CalendarType.INSTANCE.nullSafeGet( rs, names[0], session );

		if (calendar != null) {
			calendar.setTimeZone( timeZone );
		}

		return calendar;
	}

	@Override
	public void nullSafeSet(PreparedStatement st, Object value, int index, SessionImplementor session) throws HibernateException, SQLException {
		if ( value instanceof Calendar ) {
			value = convertToTimeZone( (Calendar) value, timeZone );
		}
		CalendarType.INSTANCE.nullSafeSet( st, value, index, session );
	}

	@Override
	public Object deepCopy(Object value) throws HibernateException {
		if ( value == null ) {
			return null;
		}
		return ( (Calendar) value ).clone();
	}

	@Override
	public boolean isMutable() {
		return true;
	}

	@Override
	public Serializable disassemble(Object value) throws HibernateException {
		return (Serializable) deepCopy( value );
	}

	@Override
	public Object assemble(Serializable cached, Object owner) throws HibernateException {
		return deepCopy( cached );
	}

	@Override
	public Object replace(Object original, Object target, Object owner) throws HibernateException {
		return deepCopy( original );
	}

	public static Calendar convertToTimeZone(Calendar calendar, TimeZone timeZone) {
		if ( calendar != null ) {
			final TimeZone currentTimeZone = calendar.getTimeZone();
			if ( !currentTimeZone.equals( timeZone ) ) {
				int currentOffset = currentTimeZone.getOffset( calendar.getTimeInMillis() );
				int targetOffset = timeZone.getOffset( calendar.getTimeInMillis() );
				int offset = currentOffset - targetOffset;
				calendar.add( Calendar.MILLISECOND, -offset );
				calendar.setTimeZone( timeZone );
			}
		}
		return calendar;
	}
}