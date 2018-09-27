package org.eclipse.leshan.client.demo;

import java.util.Date;
import org.eclipse.leshan.client.resource.BaseInstanceEnabler;
import org.eclipse.leshan.core.response.ExecuteResponse;
import org.eclipse.leshan.core.response.ReadResponse;

/**
 * Represents a water flow sensor that measures the flow of water in regular intervals. ( test
 * device ! )
 */
public class WaterFlowReadingsTestDevice extends BaseInstanceEnabler {

  /**
   * The Interval Period resource is an Integer value representing the width in seconds of the
   * intervals being managed by this interval data object. This resource is read only and can only
   * be changed by resource 11 (Change Interval Configuration). It is recommended that the Interval
   * Period be set to a devisor of 24 hours (86400 seconds) to provide a consistent interval period.
   * Examples of Interval Period include:- 30 = Every 30 seconds 600 = Every 10 minutes 1800 = Every
   * 30 minutes 3600 = Hourly 7200 = Every 2 hours 14400 = Every 4 hours 43200 = Every 12 hours
   * 86400 = Every Day 172600 = Every Second Day
   */
  private static final int INTERVAL_PERIOD = 6000;

  /**
   * The Interval Start Offset resource is a read only resource representing the number of seconds
   * past midnight for which the first interval is recorded. If this resource is empty, it is
   * assumed that the intervals are midnight aligned. This can be used to adjust interval
   * boundaries. As an example, an Interval Period of 3600 seconds and an Interval Start time of 300
   * represents hourly interval data, starting at 00:05.
   */
  private static final int INTERVAL_START_OFFSET = 6001;

  /**
   * The Interval UTC Offset resource is a read only resource representing the time zone offset for
   * this Interval Data instance. If this resource is empty, the application should use the UTC
   * offset provided in the Device [/3/0/14] object instance resource or UTC if not provided. UTC+X
   * [ISO 8601].
   */
  private static final int INTERVAL_UTC_OFFSET = 6002;

  /**
   * The Collection Start Time resource is a read only resource representing the time when the first
   * interval was recorded on the device. Interval times represent the end of the interval, not the
   * beginning. As an example, the first four hourly interval past midnight will have a timestamp of
   * 04:00 (adjusting for UTC offset).
   */
  private static final int INTERVAL_COLLECTION_START_TIME = 6003;

  /**
   * The Oldest Recorded Interval resource is a read-only resource representing the oldest available
   * interval on the device. Interval times represent the end of the interval, not the beginning.
   */
  private static final int OLDEST_RECORDED_INTERVAL = 6004;

  /**
   * The Last Delivered Interval is a readable and writable resource used to represent the last
   * interval delivered to the LwM2M server. Interval times represent the end of the interval, not
   * the beginning. The setting of this resource is implementation specific but should be updated
   * based on, either a Read request of the Latest Payload from the LwM2M server or via a confirmed
   * delivery of Notify operation of the Latest Payload resource. This resource is writable to allow
   * the server to adjust the Last Delivered Interval value if the server and client is out of
   * sync.
   */
  private static final int LAST_DELIVERED_INTERVAL = 6005;

  /**
   * The Latest Recorded Interval is a readable resource representing the currently highest recorded
   * interval on the device. Interval times represent the end of the interval, not the beginning.
   */
  private static final int LATEST_RECORDED_INTERVAL = 6006;

  /**
   * The Delivery Midnight Aligned is a readable and writable resource used to indicate if data is
   * delivered only to the previous midnight (1) or if part-day data can be delivered (0).
   * Calculating Midnight should consider the Interval UTC Offset resource, or if empty, the Device
   * [/3/0/14] object instance resource.
   */
  private static final int INTERVAL_DELIVERY_MIDNIGHT_ALIGNED = 6007;

  /**
   * Historical Interval Read is an executable resource designed to retrieve ad-hoc interval read
   * data. The resource takes two arguments:- Argument 0: First Interval time to Retrieve
   * represented as number of seconds since Jan 1st, 1970 in the UTC time zone. Argument 1: Last
   * interval time to Retrieve represented as number of seconds since Jan 1st, 1970 in the UTC time
   * zone. The dates should be inclusive based on the end time of the interval. The data retrieved
   * from this resource will be readable (or observable) from the Historical Read Payload Resource.
   * As an example, the Argument List to retrieve data from Midnight 2nd March (UTC+10) to Midnight
   * 6rd March (UTC+10) for a specific instance of the interval data object, would be constructed as
   * follows:- 0='1488463200',1='1488808800'
   */
  private static final int INTERVAL_HISTORICAL_READ = 6008;

  /**
   * The Historical Read Payload resource is the representation of the data requested by the
   * Historical Interval Read executable resource. The format of this Opaque value should be
   * identical to the Latest Payload resource. If no Historical Interval Read has been executed,
   * this resource should return and empty Opaque value. This resource can either be Read from the
   * Server or set up as an observation and Notified to the server as soon as the historical data is
   * available. When this payload is delivered to the LwM2M server, via either a read request or a
   * confirmed observation on this Object, Object Instance or Resource, the Historical Read Payload
   * should be set to an empty Opaque value.
   */
  private static final int INTERVAL_HISTORICAL_READ_PAYLOAD = 6009;

  /**
   * Change Interval Configuration is an executable resource designed to allow the Interval Period,
   * Interval Start Offset and Interval UTC Offset to be reconfigured. The resource takes three
   * arguments:- Argument 0: [Mandatory] Interval Period represented as an integer as defined in the
   * Interval Period Resource. Argument 1: [Optional] Interval start offset represented as an
   * integer as defined in the Interval Start Offset Resource. If not provided, leave the value as
   * per the current configuration Argument 2: [Optional] Interval UTC offset represented as a
   * String as defined in the Interval UTC Offset Resource. If not provided, leave the value as per
   * the current configuration. Depending on the specifics of the implementation of this object,
   * reconfiguring the Interval data object may result in the removal of all historical data for
   * this Interval Data Object Instance. Please consult with your device vendor as to the
   * implications of reconfiguring an Interval Data Object Instance. As an example, the Argument
   * List to change an interval data object instance from its existing values to one hour intervals,
   * midnight aligned in UTC+10 time zone:- 0='3600',1='0',1='UTC+10'
   */
  private static final int INTERVAL_RANGE_CONFIGURATION = 6010;

  /**
   * Start is an executable resource that enables the recording of interval data. The first interval
   * recorded will have a timestamp based on the Interval Start Offset resource. This executable
   * resource takes no arguments. Refer to re-usable resource LogStart for further details.
   */
  private static final int START = 6026;

  /**
   * Stop (LogStop) is an executable resource that disables the recording of interval data for this
   * object instance. This executable resource takes no arguments. Refer to re-usable resource
   * LogStop for further details.
   */
  private static final int STOP = 6027;

  /**
   * Recording Enabled is a read-only resource providing an indication of if interval data is
   * currently being recorded for this object instance. Refer to re-usable resource LogStatus for
   * further details.
   */
  private static final int STATUS = 6028;

  /**
   * The Latest Payload resource is a read-only serialised Opaque (Binary) representation of all the
   * Interval Data between the Last Delivered Interval and the Latest Recorded Interval, accounting
   * for the Delivery Midnight Aligned resource. When this payload is delivered to the LwM2M server,
   * via either a read request or a confirmed observation on this Object, Object Instance or
   * Resource, the Latest Delivered Interval should be updated. When no new data exists, an empty
   * Opaque value should be provided. The payload data can be provided in an implementation specific
   * serialisation, but by default for fixed length values should use the OMA-LwM2M CBOR format
   * encoded as follows: 1. 8-bit integer, value 2 representing OMA-LwM2M CBOR format. 2. Interval
   * Data Instance ID/ Class [16-bit integer] 3. Timestamp of first Interval [32-bit integer]
   * representing the number of seconds since Jan 1st, 1970 in the UTC time zone. 4. Interval Period
   * in seconds [32-bit integer] 5. Number of intervals in Payload [16-bit integer] 6. Number of
   * Values Per Interval [8-bit integer] 7. Size of Value 1 in bits [8-bit integer] 8. Size of Value
   * 2 in bits [8-bit integer] ... 9. Size of Value N in bits [8-bit integer] 10. Interval 1 Value 1
   * [x bits] 11. Interval 1 Value 2 [x bits] ... 12. Interval 1 Value N [x bits] ... 13. Interval N
   * Value N [x bits] If for some implementation specific reason, there are missing intervals in the
   * sequence, the payload may consist of multiple blocks of the above serialised data (appended
   * into a single binary opaque value), each block representing a continuous series of interval
   * data.
   */
  private static final int LATEST_PAYLOAD = 6029;

  @Override
  public synchronized ReadResponse read(int resourceId) {
    switch (resourceId) {
      case INTERVAL_PERIOD:
        return ReadResponse.success(resourceId, this.getIntervalPeriod());

      case INTERVAL_START_OFFSET:
        return ReadResponse.success(resourceId, this.getIntervalStartOffset());

      case INTERVAL_UTC_OFFSET:
        return ReadResponse.success(resourceId, this.getIntervalUtcOffset());

      case INTERVAL_COLLECTION_START_TIME:
        return ReadResponse.success(resourceId, this.getIntervalCollectionStartTime());

      case OLDEST_RECORDED_INTERVAL:
        return ReadResponse.success(resourceId, this.getOldestRecordedInterval());

      case LAST_DELIVERED_INTERVAL:
        return ReadResponse.success(resourceId, this.getLastDeliveredInterval());

      case LATEST_RECORDED_INTERVAL:
        return ReadResponse.success(resourceId, this.getLatestRecordedInterval());

      case INTERVAL_DELIVERY_MIDNIGHT_ALIGNED:
        return ReadResponse.success(resourceId, this.getIntervalDeliveryMidnightAligned());

      case INTERVAL_HISTORICAL_READ_PAYLOAD:
        return ReadResponse.success(resourceId, this.getIntervalHistoricalReadPayload());

      case STATUS:
        return ReadResponse.success(resourceId, this.getStatus());

      case LATEST_PAYLOAD:
        return ReadResponse.success(resourceId, this.getLatestPayload());

      default:
        return super.read(resourceId);
    }
  }

  @Override
  public ExecuteResponse execute(int resourceId, String params) {
    switch (resourceId) {
      case START:
        this.start();
        return ExecuteResponse.success();

      case STOP:
        this.stop();
        return ExecuteResponse.success();

      case INTERVAL_HISTORICAL_READ:
        this.intervalHistoricalRead();
        return ExecuteResponse.success();

      case INTERVAL_RANGE_CONFIGURATION:
        this.IntervalRangeConfiguration();
        return ExecuteResponse.success();

      default:
        return super.execute(resourceId, params);
    }
  }

  private void start() {
    System.out.println("START");
  }

  private void stop() {
    System.out.println("STOP");
  }

  private void intervalHistoricalRead() {
    System.out.println("INTERVAL_HISTORICAL_READ");
  }

  private void IntervalRangeConfiguration() {
    System.out.println("INTERVAL RANGE CONFIGURATION");
  }

  private int getIntervalPeriod() {
    return 0;
  }

  private int getIntervalStartOffset() {
    return 0;
  }

  private String getIntervalUtcOffset() {
    return "";
  }

  private Date getIntervalCollectionStartTime() {
    return new Date();
  }

  private Date getOldestRecordedInterval() {
    return new Date();
  }

  private Date getLastDeliveredInterval() {
    return new Date();
  }

  private Date getLatestRecordedInterval() {
    return new Date();
  }

  private boolean getIntervalDeliveryMidnightAligned() {
    return false;
  }

  private byte[] getIntervalHistoricalReadPayload() {
    return new byte[1];
  }

  private int getStatus() {
    return 0;
  }

  private byte[] getLatestPayload() {
    return new byte[1];
  }
}
