package br.com.sankhya.logbit.actions;

import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava;
import br.com.sankhya.extensions.actionbutton.ContextoAcao;
import br.com.sankhya.extensions.actionbutton.Registro;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.util.FinderWrapper;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.vo.EntityVO;
import br.com.sankhya.mgeserv.model.helpper.FaturamentoContratosServicosHelper;
import br.com.sankhya.modelcore.MGEModelException;
import br.com.sankhya.modelcore.auth.AuthenticationInfo;
import br.com.sankhya.modelcore.dwfdata.vo.tgf.FinanceiroVO;
import br.com.sankhya.modelcore.helper.ArmazensGeraisHelper;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import com.sankhya.util.BigDecimalUtil;
import com.sankhya.util.TimeUtils;
import org.apache.commons.net.ntp.TimeStamp;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;

public class GeraContrato implements AcaoRotinaJava {
    @Override
    public void doAction(ContextoAcao contextoAcao) throws Exception {

        Registro[] linhas = contextoAcao.getLinhas();
        EntityFacade dwf = EntityFacadeFactory.getDWFFacade();
        BigDecimal codUsuLogado = AuthenticationInfo.getCurrent().getUserID();


        // Verifica se mais de um registro (proposta) foi selecionada
        if (linhas.length > 1) contextoAcao.mostraErro("Mais de uma proposta foi selecionada. Para gerar contrato, selecione apenas uma proposta.");

        for (Registro linha : linhas) {
            BigDecimal numContrato = null;
            BigDecimal numOS = (BigDecimal) linha.getCampo("NUMOS");
            BigDecimal sequencial = (BigDecimal) linha.getCampo("SEQPROP");
            String descricaoProposta = (String) linha.getCampo("DESCRPROP");

            DynamicVO negociacaoLobgitVO = (DynamicVO) dwf.findEntityByPrimaryKeyAsVO("AD_NEGLOG", new Object[] {numOS, sequencial});
            DynamicVO negociacaoVO = (DynamicVO) dwf.findEntityByPrimaryKeyAsVO(DynamicEntityNames.ORDEM_SERVICO, numOS);
            DynamicVO prospectVO = (DynamicVO) dwf.findEntityByPrimaryKeyAsVO(DynamicEntityNames.PARCEIRO_PROSPECT, negociacaoVO.asBigDecimalOrZero("CODPAP"));

            //Collection<DynamicVO> produtosNegociacao = dwf.findByDynamicFinderAsVO(new FinderWrapper("ProdutoNegociacao", "NUMOS = ?", numOS));

            if (BigDecimalUtil.isNullOrZero(prospectVO.asBigDecimalOrZero("CODPARC"))) {
                contextoAcao.mostraErro("Prospect não foi convertido para parceiro.");
            } else {

                Collection<DynamicVO> parcelasVO = parcelas(numOS, sequencial);

                if (parcelasVO.isEmpty()) {
                    throw new MGEModelException("Preencha o parcelamento corretamente para gerar as provisões no contrato.");
                } else {
                    Timestamp dtFatur = parcelasVO.stream().findFirst().get().asTimestamp("DTFATURAMENTO");

                    DynamicVO contratoVO = (DynamicVO) dwf.getDefaultValueObjectInstance(DynamicEntityNames.CONTRATO);
                    contratoVO.setProperty("DTCONTRATO", TimeUtils.getNow());
                    contratoVO.setProperty("CODEMP", BigDecimal.ONE);
                    contratoVO.setProperty("CODPARC", prospectVO.asBigDecimalOrZero("CODPARC"));
                    contratoVO.setProperty("CODCONTATO", negociacaoVO.asBigDecimalOrZero("CODCONTATOPAP"));
                    contratoVO.setProperty("CODUSU", codUsuLogado);
                    contratoVO.setProperty("CODCENCUS", negociacaoVO.asBigDecimalOrZero("CODCENCUS"));
                    contratoVO.setProperty("RECDESP", BigDecimal.ONE);
                    if (contratoVO.containsProperty("AD_NUMEROLOGBIT"))
                        contratoVO.setProperty("AD_NUMEROLOGBIT", descricaoProposta);
                    if (contratoVO.containsProperty("AD_NOMEDOPROJETO"))
                        contratoVO.setProperty("AD_NOMEDOPROJETO", descricaoProposta);
                    if (contratoVO.containsProperty("AD_NUMOS"))
                        contratoVO.setProperty("AD_NUMOS", numOS);
                    if (contratoVO.containsProperty("AD_SEQNEGLOGBIT"))
                        contratoVO.setProperty("AD_SEQNEGLOGBIT", sequencial);
                    //contratoVO.setProperty("CODNAT", codNat);
                    //contratoVO.setProperty("CODTIPVENDA", codTipVenda);
                    contratoVO.setProperty("ATIVO", "S");
                    contratoVO.setProperty("FREQREAJ", BigDecimalUtil.valueOf(12));
                    contratoVO.setProperty("DTBASEREAJ", dtFatur);
                    contratoVO.setProperty("PARCELAATUAL", BigDecimal.ONE);
                    contratoVO.setProperty("PARCELAQTD", BigDecimal.valueOf(parcelasVO.size()));
                    contratoVO.setProperty("QTDPROVISAO", BigDecimal.valueOf(parcelasVO.size()));
                    dwf.createEntity(DynamicEntityNames.CONTRATO, (EntityVO) contratoVO);
                    numContrato = contratoVO.asBigDecimal("NUMCONTRATO");

                    DynamicVO produtoContratoVO = (DynamicVO) dwf.getDefaultValueObjectInstance(DynamicEntityNames.PRODUTO_SERVICO_CONTRATO);
                    produtoContratoVO.setProperty("NUMCONTRATO", contratoVO.asBigDecimalOrZero("NUMCONTRATO"));
                    produtoContratoVO.setProperty("CODPROD", BigDecimal.valueOf(2)); // Serviço Projeto
                    produtoContratoVO.setProperty("QTDEPREVISTA", BigDecimal.ONE);
                    produtoContratoVO.setProperty("VLRUNIT", negociacaoLobgitVO.asBigDecimalOrZero("VLRTOTAL"));
                    dwf.createEntity(DynamicEntityNames.PRODUTO_SERVICO_CONTRATO, (EntityVO) produtoContratoVO);


                    int i = 0;
                    for (DynamicVO parcela: parcelasVO) {

                        DynamicVO precoProdVO =  (DynamicVO) dwf.getDefaultValueObjectInstance(DynamicEntityNames.PRECO_CONTRATO);
                        precoProdVO.setProperty("NUMCONTRATO", contratoVO.asBigDecimalOrZero("NUMCONTRATO"));
                        precoProdVO.setProperty("CODPROD", BigDecimal.valueOf(2)); // Serviço padrão Projeto
                        precoProdVO.setProperty("CODSERV", BigDecimal.valueOf(2)); // Serviço padrão Projeto
                        precoProdVO.setProperty("REFERENCIA", parcela.asTimestamp("DTFATURAMENTO"));
                        precoProdVO.setProperty("VALOR", parcela.asBigDecimalOrZero("VLRPARRCELA"));
                        dwf.createEntity(DynamicEntityNames.PRECO_CONTRATO, (EntityVO) precoProdVO);

                        DynamicVO ocorrenciaContratoVO = (DynamicVO) dwf.getDefaultValueObjectInstance(DynamicEntityNames.OCORRENCIA_CONTRATO);
                        ocorrenciaContratoVO.setProperty("NUMCONTRATO", contratoVO.asBigDecimalOrZero("NUMCONTRATO"));
                        ocorrenciaContratoVO.setProperty("CODPROD", BigDecimal.valueOf(2)); // Serviço Projeto
                        ocorrenciaContratoVO.setProperty("CODUSU",codUsuLogado);
                        ocorrenciaContratoVO.setProperty("CODOCOR", BigDecimal.ONE);
                        ocorrenciaContratoVO.setProperty("DTOCOR", parcela.asTimestamp("DTFATURAMENTO"));
                        ocorrenciaContratoVO.setProperty("CODCONTATO", contratoVO.asBigDecimalOrZero("CODCONTATO"));
                        ocorrenciaContratoVO.setProperty("CODPARC", contratoVO.asBigDecimalOrZero("CODPARC"));
                        ocorrenciaContratoVO.setProperty("DESCRICAO", "Prestação de serviços");
                        dwf.createEntity(DynamicEntityNames.OCORRENCIA_CONTRATO, (EntityVO) ocorrenciaContratoVO);

                        FinanceiroVO finVO = (FinanceiroVO) dwf.getDefaultValueObjectInstance(DynamicEntityNames.FINANCEIRO, FinanceiroVO.class);
                        finVO.setCODEMP(BigDecimal.ONE);
                        finVO.setCODPARC(prospectVO.asBigDecimalOrZero("CODPARC"));
                        finVO.setRECDESP(BigDecimal.ONE);
                        finVO.setDESDOBRAMENTO(String.valueOf(++i));
                        finVO.setCODTIPTIT(BigDecimal.ONE);
                        finVO.setCODTIPOPER(BigDecimal.valueOf(3250));
                        finVO.setCODCENCUS(BigDecimal.valueOf(50000));
                        finVO.setCODVEND(BigDecimal.valueOf(8));
                        finVO.setNUMCONTRATO(contratoVO.asBigDecimalOrZero("NUMCONTRATO"));
                        finVO.setPROVISAO("S");
                        finVO.setDTNEG(dtFatur);
                        finVO.setProperty("REFATCON", parcela.asTimestamp("DTFATURAMENTO"));
                        finVO.setDTVENC(parcela.asTimestamp("DTRECEBIMENTO"));
                        finVO.setCODBCO(BigDecimal.ZERO);
                        finVO.setORIGEM("F");
                        finVO.setCODNAT(BigDecimal.ZERO); // Receitas de venda
                        finVO.setVLRDESDOB(parcela.asBigDecimalOrZero("VLRPARRCELA"));
                        finVO.setDESDOBDUPL("T");
                        finVO.setNUMNOTA(BigDecimal.ZERO);
                        if (finVO.getDTVENC() != null) {
                            dwf.createEntity(DynamicEntityNames.FINANCEIRO, finVO);
                        } else {
                            throw new MGEModelException("Preencha as datas de recebimento no Parcelamento da Negociação.");
                        }
                    }

                    /*FaturamentoContratosServicosHelper faturamentoContratosServicosHelper = new FaturamentoContratosServicosHelper();
                    Collection<DynamicVO> contratosVO = new ArrayList<DynamicVO>();
                    contratosVO.add(contratoVO);
                    FaturamentoContratosServicosHelper.ConfiguracaoFaturamento cfg = new FaturamentoContratosServicosHelper.ConfiguracaoFaturamento();
                    cfg.setDtFat(dtFatur);
                    cfg.setConsiderarDtRefGerarProv(true);
                    faturamentoContratosServicosHelper.setConfiguracao(cfg);
                    faturamentoContratosServicosHelper.gerarFuturasProvisoesSemFaturar(contratosVO);*/
                }

                contextoAcao.setMensagemRetorno("Contrato "+ numContrato + " criado com sucesso!");

            }

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
