package org.openmrs.module.webservices.rest.web.api.impl;

import org.hibernate.CacheMode;
import org.hibernate.Session;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.hibernate.DbSession;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.webservices.rest.web.api.RestHelperService;
import org.openmrs.module.webservices.rest.web.resource.api.SearchHandler;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingSubclassHandler;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Predicate;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.openmrs.api.context.Context.getRegisteredComponents;

/**
 * REST helper service, which must not be used outside of the REST module.
 */
public class RestHelperServiceImpl extends BaseOpenmrsService implements RestHelperService {
    
    DbSessionFactory sessionFactory;
    
    Method method;
    
    public void setSessionFactory(DbSessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }
    
    /**
     * @see org.openmrs.module.webservices.rest.web.api.RestHelperService#getObjectByUuid(Class,
     *      String)
     */
    @Override
    @Transactional(readOnly = true)
    public <T> T getObjectByUuid(Class<? extends T> type, String uuid) {
        Session session = getSession();
        CriteriaBuilder cb = session.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery((Class<T>) type);
        Root<T> root = cq.from((Class<T>) type);
        cq.select(root).where(cb.equal(root.get("uuid"), uuid));
        
        List<T> results = session.createQuery(cq).getResultList();
        return results.isEmpty() ? null : results.get(0);
    }
    
    private DbSession getSession() {
        if (method == null) {
            try {
                return sessionFactory.getCurrentSession();
            }
            catch (NoSuchMethodError error) {
                //Supports Hibernate 3 by casting org.hibernate.classic.Session to org.hibernate.Session
                try {
                    method = sessionFactory.getClass().getMethod("getCurrentSession");
                    return (DbSession) method.invoke(sessionFactory);
                }
                catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        } else {
            try {
                return (DbSession) method.invoke(sessionFactory);
            }
            catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
    
    /**
     * @see org.openmrs.module.webservices.rest.web.api.RestHelperService#getObjectById(Class,
     *      Serializable)
     */
    @Override
    public <T> T getObjectById(Class<? extends T> type, Serializable id) {
        return type.cast(getSession().get(type, id));
    }
    
    /**
     * @see org.openmrs.module.webservices.rest.web.api.RestHelperService#getObjectsByFields(Class,
     *      Field...)
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> List<T> getObjectsByFields(Class<? extends T> type, Field... fields) {
        Session session = getSession();
        CriteriaBuilder cb = session.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery((Class<T>) type);
        Root<T> root = cq.from((Class<T>) type);
        
        List<Predicate> predicates = new ArrayList<>();
        for (Field field : fields) {
            if (field != null) {
                predicates.add(cb.equal(root.get(field.getName()), field.getValue()));
            }
        }
        
        cq.select(root).where(predicates.toArray(new Predicate[0]));
        return session.createQuery(cq).getResultList();
    }
    
    /**
     * @see org.openmrs.module.webservices.rest.web.api.RestHelperService#getPatients(Collection)
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<Patient> getPatients(Collection<Integer> patientIds) {
        List<Patient> ret = new ArrayList<Patient>();
        
        if (!patientIds.isEmpty()) {
            Session session = getSession();
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Patient> cq = cb.createQuery(Patient.class);
            Root<Patient> root = cq.from(Patient.class);
            
            cq.select(root).where(
                cb.and(
                    root.get("patientId").in(patientIds),
                    cb.equal(root.get("voided"), false)
                )
            );
            
            List<Patient> temp = session.createQuery(cq)
                .setHint("org.hibernate.cacheMode", CacheMode.IGNORE)
                .getResultList();
            
            ret.addAll(temp);
        }
        
        return ret;
    }
    
    @Override
    public List<Patient> findPatientsByIdentifierStartingWith(String identifier, boolean includeAll) {
        Session session = getSession();
        CriteriaBuilder cb = session.getCriteriaBuilder();
        CriteriaQuery<Patient> cq = cb.createQuery(Patient.class);
        Root<Patient> root = cq.from(Patient.class);
        
        cq.select(root).where(cb.like(root.get("identifier"), identifier + "%"));
        
        return session.createQuery(cq).getResultList();
    }

	
}