package com.netflix.ice.basic;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.netflix.ice.common.LineItem;
import com.netflix.ice.common.ProductService;
import com.netflix.ice.common.ResourceService;
import com.netflix.ice.tag.Account;
import com.netflix.ice.tag.Product;
import com.netflix.ice.tag.Region;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class BasicResourceService extends ResourceService {
    private final static Logger logger = LoggerFactory.getLogger(BasicResourceService.class);

    private final String[] customTags;
    private List<String> userTags;
    
    @SuppressWarnings("unchecked")
	private List<List<String>> productNamesWithResources = Lists.<List<String>>newArrayList(
            Lists.newArrayList(Product.ec2, Product.ec2Instance, Product.ebs),
            Lists.newArrayList(Product.rds, Product.rdsInstance),
            Lists.newArrayList(Product.redshift),
            Lists.newArrayList(Product.s3));
    
	private List<List<Product>> productsWithResources = Lists.<List<Product>>newArrayList();

    // Map of tags where each tag has a list of aliases.
    private final Map<String, List<String>> tagKeys;
    
    // Map of tag values to canonical name. All keys are lower case.
    private final Map<String, String> tagValuesInverted;
    
    // Map containing the lineItem column indecies that match the canonical tag keys specified by CustomTags
    // Key is the Custom Tag name (without the "user:" prefix). First index in the list is always the exact
    // custom tag name match if present.
    private Map<String, List<Integer>> tagIndecies;
    
    private static final String USER_TAG_PREFIX = "user:";

    public BasicResourceService(ProductService productService, String[] customTags, String[] additionalTags,
    		Map<String, List<String>> tagKeys, Map<String, List<String>> tagValues) {
		super();
		this.customTags = customTags;
		this.tagKeys = tagKeys;
		this.tagValuesInverted = Maps.newHashMap();
		for (Entry<String, List<String>> entry: tagValues.entrySet()) {			
			for (String val: entry.getValue()) {
				this.tagValuesInverted.put(val.toLowerCase(), entry.getKey());
			}
		}
		
        for (List<String> l: productNamesWithResources) {
        	List<Product> lp = Lists.newArrayList();
        	for (String name: l) {
        		lp.add(productService.getProductByName(name));
        	}
        	productsWithResources.add(lp);
        }
		logger.info("Initialized BasicResourceService with " + productsWithResources.size() + " products");
		
		userTags = Lists.newArrayList();
		for (String tag: customTags)
			userTags.add(tag);
		for (String tag: additionalTags)
			userTags.add(tag);		
	}

	@Override
    public void init() {		
    }
	
	@Override
	public List<String> getUserTags() {
		return userTags;
	}

    @Override
    public String getResource(Account account, Region region, Product product, LineItem lineItem, long millisStart) {
        // Build the resource group name based on the values of the custom tags
        String result = "";
        for (String tag: customTags) {
        	String val = getUserTagValue(lineItem, tag);
            result = StringUtils.isEmpty(result) ? val : result + "_" + val;
        }

        // If we didn't find any of the custom tags, default to the product name
        return StringUtils.isEmpty(result) ? product.name : result;
    }
    
    @Override
    public String getUserTagValue(LineItem lineItem, String tag) {
    	// Grab the first non-empty value
    	for (int index: tagIndecies.get(tag)) {
    		if (lineItem.getResourceTagsSize() > index) {
    			String val = lineItem.getResourceTag(index);
    			if (!StringUtils.isEmpty(val)) {
	    			if (tagValuesInverted.containsKey(val.toLowerCase())) {
	    				val = tagValuesInverted.get(val.toLowerCase());
	    			}
	    			return val;
    			}
    		}
    	}
    	return null;
    }

    @Override
    public List<List<Product>> getProductsWithResources() {
        return productsWithResources;
        
//        List<List<Product>> result = Lists.newArrayList();
//        for (Product product: ReaderConfig.getInstance().productService.getProducts()) {
//            result.add(Lists.<Product>newArrayList(product));
//        }
//        return result;
    }

    @Override
    public void commit() {

    }
    
    @Override
    public void initHeader(String[] header) {
    	tagIndecies = Maps.newHashMap();
    	for (String tag: userTags) {
    		String fullTag = USER_TAG_PREFIX + tag;
    		List<Integer> indecies = Lists.newArrayList();
    		tagIndecies.put(tag, indecies);
    		
    		// First check the preferred key name
    		int index = -1;
    		for (int i = 0; i < header.length; i++) {
    			if (header[i].equals(fullTag)) {
    				index = i;
    				break;
    			}
    		}
    		if (index >= 0) {
    			indecies.add(index);
    		}
    		// Look for alternate cases
            for (int i = 0; i < header.length; i++) {
            	if (i == index) {
            		continue;	// skip the exact match we handled above
            	}
            	if (fullTag.equalsIgnoreCase(header[i])) {
            		indecies.add(i);
            	}
            }
            // Look for aliases
            if (tagKeys.containsKey(tag)) {
            	for (String alias: tagKeys.get(tag)) {
            		String fullAlias = USER_TAG_PREFIX + alias;
                    for (int i = 0; i < header.length; i++) {
                    	if (fullAlias.equalsIgnoreCase(header[i])) {
                    		indecies.add(i);
                    	}
                    }
            	}
            }
    	}
    }
}
