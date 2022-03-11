package ru.rt.utils;

import com.comptel.mds.sas5.taskengine.rmi_fifo.RMIClientCallbackHandler;
import java.rmi.RemoteException;

/**
 *
 * @author Maksim.Filatov
 */
public class RMICallbackHandler implements RMIClientCallbackHandler{

    @Override
    public boolean disconnect() throws RemoteException {	
	    return true;
    }
    
}