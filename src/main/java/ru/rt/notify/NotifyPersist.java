package ru.rt.notify;

import ru.rt.conf.Conf;
import ru.rt.dict.Params;
import ru.rt.oms.OrderStatus;
import ru.rt.oms.OrderStatusNotification;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;
import java.util.logging.Level;

@Stateless
@LocalBean
public class NotifyPersist {
    @EJB private Conf conf;

    public void saveNotify(OrderStatusNotification notification, final String logInfo, Set<String> errors) {
        Params.LOGGER.log(Level.INFO, "{0} Сохранение нотификации requestId={1} ", new Object[]{logInfo, notification.getRequestId()});
        addNotifyInDB(notification, errors);
        if (errors.isEmpty()){
	    Params.LOGGER.log(Level.INFO, "{0} Нотификация requestId={1} успешно сохранена!", new Object[]{logInfo, notification.getRequestId()});	
	}
    }  	   
	
    /* *** privates *** */

    /**
     * Сохранение нотификации в базу данных
     * @param osn
     * @param taskId - может быть null, если задача была не найдена
     * @param errors 
     */
    private void addNotifyInDB(OrderStatusNotification osn, Set<String> errors) {
        OrderStatus orderStatus = osn.getOrder();
        if (orderStatus == null) {
            throw new IllegalArgumentException("OrderStatusNotification->OrderStatus is NULL!");
        }
	final String requestId = osn.getRequestId();
        final String orderId = orderStatus.getOrderId();
        final String omsId = orderStatus.getOrderOMSId();
        final String status = orderStatus.getOrderState();
	final String sql = "INSERT INTO ilink.notify_persist (\"OrderId\", \"OmsId\", \"RequestId\", \"Notify\", \"Status\", \"TaskId\", \"ServiceGuid\", \"IsCompleted\") values(?, ?, ?, ?, ?, ?, ?, ?)";
        StringWriter sw = new StringWriter();
        try {
	    JAXBContext jaxbContext = JAXBContext.newInstance(OrderStatusNotification.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(osn, sw);
            try (Connection conn = conf.getJdbcConnection();
		PreparedStatement ps = conn.prepareStatement(sql)
	    ) {
		ps.setString(1, orderId);
		ps.setString(2, omsId);
		ps.setString(3, requestId);
		ps.setString(4, sw.toString());
		ps.setString(5, status);
		ps.setString(6, "");
		ps.setString(7, conf.getServiceGuid());
		ps.setBoolean(8, false);
		ps.executeUpdate();
	    } catch (SQLException ex) {
		Params.LOGGER.log(Level.SEVERE, "SQL State {0} error: {1}", new Object[]{ex.getSQLState(), ex.getMessage()});
		errors.add(ex.getMessage());
	    }
	} catch (JAXBException ex) {
	    errors.add(ex.getMessage());
	    Params.LOGGER.log(Level.SEVERE, null, ex);
	}
	//osn.setRequestId(genRequestId);
    }         
    
}