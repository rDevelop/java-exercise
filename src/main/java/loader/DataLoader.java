package loader;

import data.Data;
import marketdata.ExchangeRate;
import marketdata.ExchangeRateException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Implementing Loader to create Map of {@link Data} objects.<br>
 * The default method in Interface {@link Loader#readFileAsString(String)} is hidden from implementation.<br>
 */
public class DataLoader implements Loader {
    private static final Logger logger = Logger.getLogger(DataLoader.class.getName());
    private final HashMap<String, ExchangeRate> rates = new HashMap<>();

    /**
     * This generic load method uses and Type {@link Map} to maintain the data.
     *
     * @param map   The Type of Map used to contain the List of Strings
     * @param lines The List of Strings
     * @param <T>   Any Type Map
     * @return The map that was created from data lines, or null if List parameter is null.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T extends Map<String, Data>> T load(Map<String, Data> map, List<String> lines) {
        if (lines == null) {
            return null;
        }
        return (T) createMap(map, lines);
    }


    /**
     * Implementing a mapper class that takes and Type {@link Map}<br>
     * <b>Operations</b><br>
     * Return null if lines is null, or the first line doesn't contain the expected header<br>
     * Read the lines one by one.<br>
     * If this is the header line, then continue to next line.<br>
     * Create a data object.<br>
     * Insert the line into the data object.<br>
     * Get the current amount of the line(s) (This will be Overridden if valid currency is passed<br>
     * Switch on the currency and check for CHF and GBP<br>
     * Convert the amount based on currency<br>
     * Set the converted amount.<br>
     * Use the {@link Data#getKey()} method to use as the hash key<br>
     * Check for key in Map, before creating a new Data object.<br>
     * If the object exists, then get the existing object and average the data.<br>
     * Set the existing object with new average and amounts to the original Data object<br>
     * Add the Data object to the map<br>
     * Return the map when all lines are iterated through.
     *
     * @param map   Map that will hold the List of Strings
     * @param lines Data lines from file
     * @return The populated map or null
     */
    private Map<String, Data> createMap(Map<String, Data> map, List<String> lines) {
        // stream the list strings and find all non-empty rows that have 7 elements after splitting tabs
        // This should find most bogus lines in the files, but there could always be worse out there.
        List<String> filteredList = lines.stream()
                .filter(l -> !l.isEmpty())
                .filter(l -> l.split("\t").length == 7)
                .collect(Collectors.toList());
        if (filteredList.isEmpty() || !filteredList.get(0).contains("Company Code")) {
            return Collections.emptyMap();
        }
        for (String line : filteredList) {
            if (line.contains("Company Code")) {
                continue;
            }
            Data d = new Data();
            d.insertDataLine(line);
            BigDecimal bd = d.getAmount();
            switch (d.getCurrency()) {
                // check the currency
                case "CHF":
                case "GBP":
                    // if currency is CHF, or GBP, convert amount to EUR
                    bd = euroConversion(bd, d.getCurrency());
                    break;
                default:
                    break;
            }
            d.setAmounts(bd);
            String key = d.getKey();
            if (map.get(key) != null) {
                Data existingKey = map.get(key);
                existingKey.average(d.getAmount());
                d = existingKey;
            }
            map.put(key, d);
        }
        return map;
    }

    /**
     * Method to add Exchange rates to the instance field {@link DataLoader#rates}
     *
     * @param identifier         String representation of Currency. This is the hash key
     * @param exchangeRate exchange rate that attaches to the key
     */
    public void addExchangeRate(String identifier, ExchangeRate exchangeRate) {
        rates.put(identifier, exchangeRate);
    }

    /**
     * Convert amount to amount * rate conversion
     *
     * @param amount
     * @param currency
     * @return BigDecimal newAmount
     */
    public BigDecimal euroConversion(BigDecimal amount, String currency) {
        BigDecimal convertedAmount = amount;
        try {
            // multiply the BigDecimal amount by the converted exchange rate.
            convertedAmount = amount.multiply(
                    BigDecimal.valueOf(
                            // use the currency of this line to find the /USD to EUR/USD rate.
                            ExchangeRate.getExchangeRate(
                                    rates.get(currency + "/USD"), rates.get("EUR/USD"))));

        } catch (ExchangeRateException e) {
            logger.log(Level.WARNING, "Amount is unchanged after this exception", e);

        }
        return convertedAmount;
    }
}


