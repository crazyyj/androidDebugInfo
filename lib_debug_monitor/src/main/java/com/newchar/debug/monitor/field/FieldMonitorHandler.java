package com.newchar.debug.monitor.field;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public class FieldMonitorHandler implements InvocationHandler, MonitorHandler {

    private Object mInstanceObj;
    private Map<String, Object> mFieldAndValues;
    private PropertyChangeSupport mChangeSupport;
    private PropertyChangeListener mPropertyChangeListener;

    public FieldMonitorHandler(Object obj, String... fields) {
        mInstanceObj = obj;
        mChangeSupport = new PropertyChangeSupport(mInstanceObj);
        addMonitorField(fields);
    }

    private void initFieldValueMap(Map<String, Object> fieldAndValues, Object obj, String[] fields) {
        Field privateField;
        Object fieldValue;
        for (String field : fields) {
            try {
                privateField = obj.getClass().getDeclaredField(field);
                final boolean oldAccessible = privateField.isAccessible();
                privateField.setAccessible(true);
                fieldValue = privateField.get(obj);
                fieldAndValues.put(field, fieldValue);
                privateField.setAccessible(oldAccessible);
                addFieldChangeListener(field);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 禁止重复添加
     *
     * @param fields 要监听的字段值
     */
    public void addMonitorField(String... fields) {
        if (fields != null) {
            if (mFieldAndValues == null) {
                mFieldAndValues = new HashMap<>(fields.length);
            }
            initFieldValueMap(mFieldAndValues, mInstanceObj, fields);
        }
    }

    @Override
    public void removeMonitorField(String... fieldNames) {
        if (fieldNames != null && fieldNames.length > 1) {
            if (mFieldAndValues != null) {
                for (String fieldName : fieldNames) {
                    mFieldAndValues.remove(fieldName);
                }
            }
        }
    }

    public Object newProxy() {
        return Proxy.newProxyInstance(
                mInstanceObj.getClass().getClassLoader(),
                mInstanceObj.getClass().getInterfaces(), this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object methodInvokeResult = method.invoke(mInstanceObj, args);
        checkProperty();
        return methodInvokeResult;
    }

    private void checkProperty() {
        if (mInstanceObj == null) {
            return;
        }
        Set<String> fields = mFieldAndValues.keySet();
        Field privateField;
        for (String field : fields) {
            try {
                privateField = mInstanceObj.getClass().getDeclaredField(field);
                if (field.equals(privateField.getName())) {
                    privateField.setAccessible(true);
                    Object oldValue = mFieldAndValues.get(field);
                    Object newValue = privateField.get(mInstanceObj);
                    if (newValue == null && oldValue == null) {
                        return;
                    }
                    if (oldValue != null && !oldValue.equals(newValue)) {
                        onNewValue(field, oldValue, newValue);
                    } else if (!newValue.equals(oldValue)) {
                        onNewValue(field, oldValue, newValue);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void onNewValue(String field, Object oldValue, Object newValue) {
        if (mFieldAndValues != null) {
            mFieldAndValues.put(field, newValue);
        }
        if (mChangeSupport != null) {
            mChangeSupport.firePropertyChange(field, oldValue, newValue);
        }
    }

    private void addFieldChangeListener(String field) {
        if (mChangeSupport != null && !mChangeSupport.hasListeners(field)) {
            mChangeSupport.addPropertyChangeListener(field, mChangeListener);
        }
    }

    public void setChangeListener(PropertyChangeListener changeListener) {
        this.mPropertyChangeListener = changeListener;
    }

    private final PropertyChangeListener mChangeListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (mPropertyChangeListener != null) {
                mPropertyChangeListener.propertyChange(evt);
            }
        }
    };

    public void release() {
        mInstanceObj = null;

        mChangeSupport.removePropertyChangeListener(mChangeListener);
        mChangeSupport = null;

        mPropertyChangeListener = null;
        mFieldAndValues.clear();
    }

}
