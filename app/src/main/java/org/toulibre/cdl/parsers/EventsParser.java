package org.toulibre.cdl.parsers;

import org.toulibre.cdl.model.Day;
import org.toulibre.cdl.model.Event;
import org.toulibre.cdl.model.Link;
import org.toulibre.cdl.model.Person;
import org.toulibre.cdl.model.Track;
import org.toulibre.cdl.utils.DateUtils;
import org.toulibre.cdl.utils.StringUtils;
import org.xmlpull.v1.XmlPullParser;

import android.text.TextUtils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Main parser for CDL schedule data in pentabarf XML format.
 *
 * @author Christophe Beyls
 */
public class EventsParser extends IterableAbstractPullParser<Event> {

    private final DateFormat DATE_FORMAT = DateUtils.withFrenchTimeZone(new SimpleDateFormat("yyyy-MM-dd", Locale.US));

    // Calendar used to compute the events time, according to French timezone
    private final Calendar calendar = Calendar.getInstance(DateUtils.getFrenchTimeZone(), Locale.US);

    private Day currentDay;

    private String currentRoom;

    private Track currentTrack;

    /**
     * Returns the hours portion of a time string in the "hh:mm" format, without allocating objects.
     *
     * @param time string in the "hh:mm" format
     * @return hours
     */
    private static int getHours(String time) {
        return (Character.getNumericValue(time.charAt(0)) * 10) + Character.getNumericValue(time.charAt(1));
    }

    /**
     * Returns the minutes portion of a time string in the "hh:mm" format, without allocating objects.
     *
     * @param time string in the "hh:mm" format
     * @return minutes
     */
    private static int getMinutes(String time) {
        return (Character.getNumericValue(time.charAt(3)) * 10) + Character.getNumericValue(time.charAt(4));
    }

    @Override
    protected boolean parseHeader(XmlPullParser parser) throws Exception {
        while (!isEndDocument()) {
            if (isStartTag("schedule")) {
                return true;
            }

            parser.next();
        }
        return false;
    }

    @Override
    protected Event parseNext(XmlPullParser parser) throws Exception {
        while (!isNextEndTag("schedule")) {
            if (isStartTag()) {

                switch (parser.getName()) {
                    case "day":
                        currentDay = new Day();
                        currentDay.setIndex(Integer.parseInt(parser.getAttributeValue(null, "index")));
                        currentDay.setDate(DATE_FORMAT.parse(parser.getAttributeValue(null, "date")));
                        break;
                    case "room":
                        currentRoom = parser.getAttributeValue(null, "name");
                        break;
                    case "event":
                        Event event = new Event();
                        event.setId(Long.parseLong(parser.getAttributeValue(null, "id")));
                        event.setDay(currentDay);
                        event.setRoomName(currentRoom);
                        // Initialize empty lists
                        List<Person> persons = new ArrayList<>();
                        event.setPersons(persons);
                        List<Link> links = new ArrayList<>();
                        event.setLinks(links);

                        String duration = null;
                        String trackName = "";
                        Track.Type trackType = Track.Type.conference;

                        while (!isNextEndTag("event")) {
                            if (isStartTag()) {

                                switch (parser.getName()) {
                                    case "start":
                                        String time = parser.nextText();
                                        if (!TextUtils.isEmpty(time)) {
                                            calendar.setTime(currentDay.getDate());
                                            calendar.set(Calendar.HOUR_OF_DAY, getHours(time));
                                            calendar.set(Calendar.MINUTE, getMinutes(time));
                                            event.setStartTime(calendar.getTime());
                                        }
                                        break;
                                    case "duration":
                                        duration = parser.nextText();
                                        break;
                                    case "slug":
                                        event.setSlug(parser.nextText());
                                        break;
                                    case "title":
                                        event.setTitle(parser.nextText());
                                        break;
                                    case "subtitle":
                                        event.setSubTitle(parser.nextText());
                                        break;
                                    case "track":
                                        trackName = parser.nextText();
                                        break;
                                    case "type":
                                        String typeName = parser.nextText();
                                        if (!TextUtils.isEmpty(typeName)) {
                                            String cleanedTypeName = StringUtils.removeDiacritics(typeName)
                                                    .toLowerCase(Locale.FRENCH);
                                            try {
                                                trackType = Enum.valueOf(Track.Type.class, cleanedTypeName);
                                            } catch (IllegalArgumentException e) {
                                                trackType = Track.Type.conference;
                                            }
                                        }
                                        break;
                                    case "abstract":
                                        event.setAbstractText(parser.nextText());
                                        break;
                                    case "description":
                                        event.setDescription(parser.nextText());
                                        break;
                                    case "persons":
                                        while (!isNextEndTag("persons")) {
                                            if (isStartTag("person")) {
                                                Person person = new Person();
                                                person.setId(Long.parseLong(parser.getAttributeValue(null, "id")));
                                                person.setName(parser.nextText());

                                                persons.add(person);
                                            }
                                        }
                                        break;
                                    case "links":
                                        while (!isNextEndTag("links")) {
                                            if (isStartTag("link")) {
                                                Link link = new Link();
                                                link.setUrl(parser.getAttributeValue(null, "href"));
                                                link.setDescription(parser.nextText());

                                                links.add(link);
                                            }
                                        }
                                        break;
                                    default:
                                        skipToEndTag();
                                        break;
                                }
                            }
                        }

                        if ((event.getStartTime() != null) && !TextUtils.isEmpty(duration)) {
                            calendar.add(Calendar.HOUR_OF_DAY, getHours(duration));
                            calendar.add(Calendar.MINUTE, getMinutes(duration));
                            event.setEndTime(calendar.getTime());
                        }

                        if ((currentTrack == null) || !trackName.equals(currentTrack.getName()) || (trackType != currentTrack.getType())) {
                            currentTrack = new Track(trackName, trackType);
                        }
                        event.setTrack(currentTrack);

                        return event;
                    default:
                        skipToEndTag();
                        break;
                }
            }
        }
        return null;
    }
}
