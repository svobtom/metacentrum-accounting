package cz.cesnet.meta.accounting.web;

import java.util.Date;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import net.sourceforge.stripes.action.DefaultHandler;
import net.sourceforge.stripes.action.ForwardResolution;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.integration.spring.SpringBean;
import net.sourceforge.stripes.validation.DateTypeConverter;
import net.sourceforge.stripes.validation.Validate;
import cz.cesnet.meta.accounting.displaytag.PaginationFilter;
import cz.cesnet.meta.accounting.server.service.KernelRecordManager;
import cz.cesnet.meta.accounting.server.util.Page;

public class BinariesUserTime extends AccountingWebBase {

  @SpringBean
  KernelRecordManager kernelRecordManager;
  
  Page binaryStats;
  
  private int periodInDays;
  private int number;
  @Validate(converter=DateTypeConverter.class)
  private Date fromDate;
  @Validate(converter=DateTypeConverter.class)
  private Date toDate;
  
  @DefaultHandler
  public Resolution view() {    
    if (toDate == null) {
      toDate = new LocalDate().toDateTimeAtStartOfDay().toDate();
    }
    if (fromDate == null) {
      if (periodInDays == 0) {
        periodInDays = 1;
      }
      fromDate = new DateTime(toDate.getTime()).minusDays(periodInDays).toDate();
    }
    binaryStats = kernelRecordManager.getBinariesStats(fromDate, toDate, getPageNumber(), PaginationFilter.DEFAULT_PAGE_SIZE_SHORT, getPageSize(), getSortColumn(), isAscending());     
    return new ForwardResolution("/viewBinariesStats.jsp");
  }
    
  public int getPeriodInDays() {
    return periodInDays;
  }

  public void setPeriodInDays(int periodInDays) {
    this.periodInDays = periodInDays;
  }  

  public Date getToDate() {
    return toDate;
  }

  public void setToDate(Date toDate) {
    this.toDate = toDate;
  }

  public int getNumber() {
    return number;
  }

  public void setNumber(int number) {
    this.number = number;
  }

  public Page getBinaryStats() {
    return binaryStats;
  }

  public void setBinaryStats(Page binaryStats) {
    this.binaryStats = binaryStats;
  }

  public Date getFromDate() {
    return fromDate;
  }

  public void setFromDate(Date fromDate) {
    this.fromDate = fromDate;
  }
  
}
