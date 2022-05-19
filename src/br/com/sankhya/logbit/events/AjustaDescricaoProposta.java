package br.com.sankhya.logbit.events;

import br.com.sankhya.extensions.eventoprogramavel.EventoProgramavelJava;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.event.PersistenceEvent;
import br.com.sankhya.jape.event.TransactionContext;
import br.com.sankhya.jape.util.FinderWrapper;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.vo.EntityVO;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;

import java.util.Collection;

public class AjustaDescricaoProposta implements EventoProgramavelJava {
    @Override
    public void beforeInsert(PersistenceEvent persistenceEvent) throws Exception {

    }

    @Override
    public void beforeUpdate(PersistenceEvent persistenceEvent) throws Exception {
        DynamicVO negVO = (DynamicVO) persistenceEvent.getVo();
        final boolean isModifingIdentificador = persistenceEvent.getModifingFields().isModifing("IDENTIFICADOR");

        if (isModifingIdentificador) {
            EntityFacade dwfFacade = EntityFacadeFactory.getDWFFacade();
            Collection<DynamicVO> propostas = dwfFacade.findByDynamicFinderAsVO(new FinderWrapper("AD_TADPROPOSTA", "this.NUMOS = ?", negVO.asBigDecimal("NUMOS")));

            for (DynamicVO propVO: propostas) {

                propVO.setProperty("DESCRPROJ", negVO.asString("IDENTIFICADOR"));
                dwfFacade.saveEntity("AD_TADPROPOSTA", (EntityVO) propVO);

            }

        }

    }

    @Override
    public void beforeDelete(PersistenceEvent persistenceEvent) throws Exception {

    }

    @Override
    public void afterInsert(PersistenceEvent persistenceEvent) throws Exception {

    }

    @Override
    public void afterUpdate(PersistenceEvent persistenceEvent) throws Exception {

    }

    @Override
    public void afterDelete(PersistenceEvent persistenceEvent) throws Exception {

    }

    @Override
    public void beforeCommit(TransactionContext transactionContext) throws Exception {

    }
}
