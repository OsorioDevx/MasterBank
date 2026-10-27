package main.java.br.com.masterbank.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
/*formatação para padrão brasileiro pt-BR
*
* Decimal format não é thread-safe justamente pra que o saldo dos clientes não se misturem, opção escolhida
* para garantir segurança*/


/*classe final que não pode ser herdada, as regras de negócio e política do banco permanecem imutáveis*/
public final class Formatos {
    public static final Locale PT_BR = Locale.of("pt", "BR");

    private static final DateTimeFormatter DATA =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss");

    /*classe formatos vazia e PRIVATE pra que ninguém use essa classe e o Java não crie um construtor vazio
    * padrão para a classe Formatos*/
    private Formatos(){

    }

    /*ternário com uma condição, se a data for null ele mostra um traço na tela*/
    public static String data(LocalDate data){
        return data == null ? "-" : DATA.format(data);
    }

    public static String dataHora(LocalDateTime dataHora){
        return dataHora == null ? "-": DATA_HORA.format(dataHora);
    }


    /*faz um catch no erro se o usuário digitar uma data inválida e dispara uma mensagem de erro sem parar
    * o sistema*/
    public static LocalDate lerData(String texto){
        try{
            return LocalDate.parse(texto == null ? "" : texto.trim(), DATA);
        } catch(DateTimeParseException erro){
            throw new IllegalArgumentException("Data inválida, use o formato padrão dd/mm/aaaa");
        }
    }

    /*formata fração pra percentual*/
    public static String percentual(BigDecimal fracao){
        BigDecimal pct = fracao.movePointRight(2).setScale(2, RoundingMode.HALF_EVEN);
        return formatoDecimal().format(pct) + "%";
    }

    /*formatação padrão br*/
    public static String numero(BigDecimal valor){
        return formatoDecimal().format(valor.setScale(2, RoundingMode.HALF_EVEN));
    }

    public static String ouTraco(String texto){
        return texto == null || texto.isBlank() ? "-" : texto;
    }

    /*ajusta o texto pra uma largura fixa*/
    public static String ajustar(String texto, int largura){
        String t = ouTraco(texto);
        if(t.length() <= largura){
            return String.format("%" + largura + "s", t);
        }
        /*a tesoura pra cortar se não couber no extrato*/
        return t.substring(0, largura - 1)+ "-";
    }

    public static String direita(String texto, int largura){
        return String.format("%" + largura + "s", ouTraco(texto));
    }


    public static DecimalFormat formatoDecimal(){
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR));
        formato.setRoundingMode(RoundingMode.HALF_EVEN);
        return formato;
    }




}