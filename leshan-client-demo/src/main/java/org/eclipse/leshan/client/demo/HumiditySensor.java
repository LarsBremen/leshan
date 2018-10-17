package org.eclipse.leshan.client.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.leshan.client.resource.BaseInstanceEnabler;
import org.eclipse.leshan.core.response.ExecuteResponse;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.util.NamedThreadFactory;

public class HumiditySensor extends BaseInstanceEnabler {

  private static final String UNIT_PERCENT = "%";
  private static final int SENSOR_VALUE = 5700;
  private static final int UNITS = 5701;
  private static final int MAX_MEASURED_VALUE = 5601;
  private static final int MIN_MEASURED_VALUE = 5602;
  private static final int RESET_MIN_MAX_MEASURED_VALUES = 5605;
  private final ScheduledExecutorService scheduler;
  private double currentHumid = 50d;
  private double minMeasuredValue = Double.MAX_VALUE;
  private double maxMeasuredValue = Double.MIN_VALUE;

  public HumiditySensor() {
    this.scheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Humidity Sensor"));
    scheduler.scheduleAtFixedRate(new Runnable() {

      @Override
      public void run() {
        updateHumidity();
      }
    }, 2, 2, TimeUnit.SECONDS);
  }

  @Override
  public synchronized ReadResponse read(int resourceId) {
    switch (resourceId) {
      case MIN_MEASURED_VALUE:
        return ReadResponse.success(resourceId, getTwoDigitValue(minMeasuredValue));
      case MAX_MEASURED_VALUE:
        return ReadResponse.success(resourceId, getTwoDigitValue(maxMeasuredValue));
      case SENSOR_VALUE:
        return ReadResponse.success(resourceId, getTwoDigitValue(currentHumid));
      case UNITS:
        return ReadResponse.success(resourceId, UNIT_PERCENT);
      default:
        return super.read(resourceId);
    }
  }

  @Override
  public synchronized ExecuteResponse execute(int resourceId, String params) {
    switch (resourceId) {
      case RESET_MIN_MAX_MEASURED_VALUES:
        resetMinMaxMeasuredValues();
        return ExecuteResponse.success();
      default:
        return super.execute(resourceId, params);
    }
  }

  private double getTwoDigitValue(double value) {
    BigDecimal toBeTruncated = BigDecimal.valueOf(value);
    return toBeTruncated.setScale(2, RoundingMode.HALF_UP).doubleValue();
  }

  private synchronized boolean updateHumidity() {
    try {
      currentHumid = 10.;
      Integer changedResource = adjustMinMaxMeasuredValue(currentHumid);
      if (changedResource != null) {
        fireResourcesChange(SENSOR_VALUE, changedResource);
      } else {
        fireResourcesChange(SENSOR_VALUE);
      }
    } catch (Exception e) {
      System.out.println("Error while updating Humidity: "+ e);
//			e.printStackTrace();
    }
    return true;
  }

  private Integer adjustMinMaxMeasuredValue(double newHumidity) {

    if (newHumidity > maxMeasuredValue) {
      maxMeasuredValue = newHumidity;
      return MAX_MEASURED_VALUE;
    } else if (newHumidity < minMeasuredValue) {
      minMeasuredValue = newHumidity;
      return MIN_MEASURED_VALUE;
    } else {
      return null;
    }
  }

  private void resetMinMaxMeasuredValues() {
    minMeasuredValue = currentHumid;
    maxMeasuredValue = currentHumid;
  }
}
