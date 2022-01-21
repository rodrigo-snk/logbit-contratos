package br.com.sankhya.logbit.actions;

import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava;
import br.com.sankhya.extensions.actionbutton.ContextoAcao;
import br.com.sankhya.extensions.actionbutton.Registro;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.vo.EntityVO;
import br.com.sankhya.mgeserv.model.helpper.FaturamentoContratosServicosHelper;
import br.com.sankhya.modelcore.MGEModelException;
import br.com.sankhya.modelcore.auth.AuthenticationInfo;
import br.com.sankhya.modelcore.helper.ArmazensGeraisHelper;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import com.sankhya.util.BigDecimalUtil;
import com.sankhya.util.TimeUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;

public class GeraProvisao implements AcaoRotinaJava {
    @Override
    public void doAction(ContextoAcao contextoAcao) throws Exception {

        Registro[] linhas = contextoAcao.getLinhas();
        EntityFacade dwf = EntityFacadeFactory.getDWFFacade();
        BigDecimal codUsuLogado = AuthenticationInfo.getCurrent().getUserID();


        // Verifica se mais de um registro (proposta) foi selecionada
        if (linhas.length > 1) contextoAcao.mostraErro("Mais de uma proposta foi selecionada. Para gerar contrato, selecione apenas uma proposta.");

        for (Registro linha : linhas) {
            BigDecimal numContrato = (BigDecimal) linha.getCampo("NUMCONTRATO");

            DynamicVO contratoVO = (DynamicVO) dwf.findEntityByPrimaryKeyAsVO(DynamicEntityNames.CONTRATO, numContrato);

            FaturamentoContratosServicosHelper faturamentoContratosServicosHelper = new FaturamentoContratosServicosHelper();
            Collection<DynamicVO> contratosVO = new ArrayList<DynamicVO>();
            contratosVO.add(contratoVO);
            FaturamentoContratosServicosHelper.ConfiguracaoFaturamento cfg = new FaturamentoContratosServicosHelper.ConfiguracaoFaturamento();
            cfg.setDtFat(contratoVO.asTimestamp("DTREFPROXFAT"));
            cfg.setCodTipOper(BigDecimal.valueOf(3250));
            cfg.setCodTipTit(BigDecimal.ONE);
            //cfg.setCodEmp(BigDecimal.ONE);
            cfg.setConsiderarDtRefGerarProv(true);
            faturamentoContratosServicosHelper.setConfiguracao(cfg);
            faturamentoContratosServicosHelper.gerarFuturasProvisoesSemFaturar(contratosVO);

        }

    }





    public static Collection<DynamicVO> parcelas(BigDecimal nrOS, BigDecimal sequencia) throws MGEModelException {
        Collection<DynamicVO> parcelasVO;
        JapeSession.SessionHandle hnd = null;
        JdbcWrapper jdbc = null;
        try{
            hnd = JapeSession.open();
            jdbc = EntityFacadeFactory.getDWFFacade().getJdbcWrapper();
            jdbc.openSession();

            NativeSql sql = new NativeSql(jdbc);
            sql.appendSql("select ite.* from ad_cabparc cab\n" +
                    "join ad_iteparc ite on cab.nuparc = ite.nuparc \n" +
                    "where nros = :NROS and seqneglogbit = :SEQNEGLOGBIT and ite.nuparc = (select max(ite.nuparc) from ad_cabparc cab\n" +
                    "join ad_iteparc ite on cab.nuparc = ite.nuparc  where nros = :NROS and seqneglogbit = :SEQNEGLOGBIT)");
            sql.setNamedParameter("NROS", nrOS);
            sql.setNamedParameter("SEQNEGLOGBIT", sequencia);
            sql.executeQuery();
            parcelasVO = sql.asVOCollection("AD_ITEPARC");

            NativeSql.releaseResources(sql);

        } catch(Exception e) {
            throw new MGEModelException(e);
        } finally {
            JdbcWrapper.closeSession(jdbc);
            JapeSession.close(hnd);
        }
        
        return parcelasVO;

    }
}
