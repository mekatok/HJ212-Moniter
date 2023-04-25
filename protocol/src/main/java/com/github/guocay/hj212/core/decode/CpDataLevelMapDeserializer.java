package com.github.guocay.hj212.core.decode;


import com.github.guocay.hj212.core.ProtocolParser;
import com.github.guocay.hj212.core.VerifyUtil;
import com.github.guocay.hj212.exception.ProtocolFormatException;
import com.github.guocay.hj212.exception.SegmentFormatException;
import com.github.guocay.hj212.model.DataFlag;
import com.github.guocay.hj212.model.verify.DataElement;
import com.github.guocay.hj212.model.verify.PacketElement;
import com.github.guocay.hj212.model.verify.ProtocolCpDataLevelMap;
import com.github.guocay.hj212.model.verify.ProtocolMap;
import com.github.guocay.hj212.model.verify.groups.ModeGroup;
import com.github.guocay.hj212.model.verify.groups.ProtocolMapLevelGroup;
import com.github.guocay.hj212.model.verify.groups.VersionGroup;
import com.github.guocay.hj212.segment.config.Configurator;
import com.github.guocay.hj212.segment.config.Configured;
import com.github.guocay.hj212.segment.core.SegmentParser;
import com.github.guocay.hj212.segment.core.decode.SegmentDeserializer;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.groups.Default;

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.PushbackReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static com.github.guocay.hj212.core.ProtocolParser.crc16Checkout;
import static com.github.guocay.hj212.core.feature.VerifyFeature.ALLOW_MISSING_FIELD;
import static com.github.guocay.hj212.core.feature.VerifyFeature.DATA_CRC;
import static com.github.guocay.hj212.core.feature.VerifyFeature.DATA_LEN_RANGE;
import static com.github.guocay.hj212.core.feature.VerifyFeature.THROW_ERROR_VERIFICATION_FAILED;
import static com.github.guocay.hj212.core.feature.VerifyFeature.USE_VERIFICATION;
import static com.github.guocay.hj212.core.validator.clazz.FieldValidator.create_format_exception;

/**
 * 数据区 级别 反序列化器
 * @author aCay
 */
@SuppressWarnings({"rawtypes","unused"})
public class CpDataLevelMapDeserializer
        implements ProtocolDeserializer<Map<String,Object>>, Configured<CpDataLevelMapDeserializer> {

    private int verifyFeature;
    private Configurator<SegmentParser> segmentParserConfigurator;
    private SegmentDeserializer<Map<String,Object>> segmentDeserializer;
    private Validator validator;

    @Override
    public void configured(Configurator<CpDataLevelMapDeserializer> configurator){
        configurator.config(this);
    }

    @Override
    public Map<String, Object> deserialize(ProtocolParser parser) throws IOException, ProtocolFormatException {
        parser.readHeader();
        int len = parser.readInt32(10);
        if(len == -1){
            ProtocolFormatException.length_not_range(PacketElement.DATA_LEN,len,4,4);
        }
        if(DATA_LEN_RANGE.enabledIn(verifyFeature)){
            VerifyUtil.verifyRange(len,0,1024, PacketElement.DATA_LEN);
        }
        char[] data = parser.readData(len);
        int crc = parser.readInt32(16);

        if(DATA_CRC.enabledIn(verifyFeature)){
            if(crc == -1 ||
                    crc16Checkout(data,len) != crc){
                ProtocolFormatException.crc_verification_failed(PacketElement.DATA,data,crc);
            }
        }
        parser.readFooter();

        return deserialize(data);
    }

    public Map<String, Object> deserialize(char[] data) throws IOException, ProtocolFormatException {
        PushbackReader reader = new PushbackReader(new CharArrayReader(data));
        SegmentParser parser = new SegmentParser(reader);
        parser.configured(segmentParserConfigurator);

        Map<String,Object> result = null;
        try {
            result = segmentDeserializer.deserialize(parser);
        } catch (SegmentFormatException e) {
            ProtocolFormatException.segment_exception(e);
        }

        if(USE_VERIFICATION.enabledIn(verifyFeature)){
            verifyByType(result);
        }
        return result;
    }

	private void verifyByType(Map<String, Object> result) throws ProtocolFormatException {
        ProtocolCpDataLevelMap t212Map = ProtocolMap.createCpDataLevel(result);
        ProtocolCpDataLevelMap.Cp cp = t212Map.getCp();

        List<Class> groups = new ArrayList<>();
        groups.add(Default.class);
        int flag = 0;
        String name = DataElement.Flag.name();
        if(result.containsKey(DataElement.Flag.name())){
            String f = (String) result.get(DataElement.Flag.name());
            flag = Integer.valueOf(f);
        }
        if(!DataFlag.V0.isMarked(flag)){
            groups.add(VersionGroup.V2017.class);
        }else{
            groups.add(VersionGroup.V2005.class);
        }
        if(DataFlag.D.isMarked(flag)){
            groups.add(ModeGroup.UseSubPacket.class);
        }

        Set<ConstraintViolation<ProtocolMap>> constraintViolationSet = validator.validate(t212Map,groups.toArray(new Class[]{}));
        Set<ConstraintViolation<ProtocolMap>> constraintViolationSet2 = validator.validate(cp,groups.toArray(new Class[]{}));
        constraintViolationSet.addAll(constraintViolationSet2);
        if(!constraintViolationSet.isEmpty()) {
            if(THROW_ERROR_VERIFICATION_FAILED.enabledIn(verifyFeature)){
                create_format_exception(constraintViolationSet,result);
            }else{
                //TODO set context
            }
        }
    }

	@Deprecated
    private void verifyByVersion(Map<String, Object> result) throws ProtocolFormatException {
        List<Class> groups = new ArrayList<>();
        groups.add(Default.class);
        groups.add(ProtocolMapLevelGroup.DataLevel.class);

        int flag = 0;
        ProtocolMap t212Map;
        if(result.containsKey(DataElement.Flag.name())){
            String f = (String) result.get(DataElement.Flag.name());
            flag = Integer.valueOf(f);
        }
        if(DataFlag.V0.isMarked(flag)){
            t212Map = ProtocolMap.create2017(result);
        }else{
            t212Map = ProtocolMap.create2005(result);
        }
        if(DataFlag.D.isMarked(flag)){
            groups.add(ModeGroup.UseSubPacket.class);
        }

        Set<ConstraintViolation<ProtocolMap>> constraintViolationSet = validator.validate(t212Map,groups.toArray(new Class[]{}));
        if(!constraintViolationSet.isEmpty()) {
            create_format_exception(constraintViolationSet,result);
        }
    }

    @SuppressWarnings("unchecked")
	@Deprecated
    private void verify(Map<String, Object> result) throws ProtocolFormatException {
        if(!ALLOW_MISSING_FIELD.enabledIn(verifyFeature)){
            Stream<DataElement> stream = Stream.of(DataElement.values())
                    .filter(DataElement::isRequired);
            if(result.containsKey(DataElement.Flag.name())){
                String f = (String) result.get(DataElement.Flag.name());
                int flag = Integer.valueOf(f);
                if(DataFlag.D.isMarked(flag)){
                    stream = Stream.concat(stream,Stream.of(DataElement.PNO, DataElement.PNUM));
                }
            }

            Optional<DataElement> missing = stream
                    .filter(e -> !result.containsKey(e.name()))
                    .findFirst();
            if(missing.isPresent()){
                ProtocolFormatException.field_is_missing(PacketElement.DATA,missing.get().name());
            }
        }
        if(result.containsKey(DataElement.CP.name())){
            Map<String,Object> cp = (Map) result.get(DataElement.CP.name());
            if(!ALLOW_MISSING_FIELD.enabledIn(verifyFeature)){
                Stream<DataElement> stream = Stream.of(DataElement.values())
                        .filter(DataElement::isRequired);
                if(result.containsKey(DataElement.Flag.name())){
                    String f = (String) result.get(DataElement.Flag.name());
                    int flag = Integer.valueOf(f);
                    if(DataFlag.D.isMarked(flag)){
                        stream = Stream.concat(stream,Stream.of(DataElement.PNO, DataElement.PNUM));
                    }
                }

                Optional<DataElement> missing = stream
                        .filter(e -> !result.containsKey(e.name()))
                        .findFirst();
                if(missing.isPresent()){
                    ProtocolFormatException.field_is_missing(PacketElement.DATA,missing.get().name());
                }
            }
        }
    }

    public void setVerifyFeature(int verifyFeature) {
        this.verifyFeature = verifyFeature;
    }

    public void setSegmentParserConfigurator(Configurator<SegmentParser> segmentParserConfigurator) {
        this.segmentParserConfigurator = segmentParserConfigurator;
    }

    public void setSegmentDeserializer(SegmentDeserializer<Map<String, Object>> segmentDeserializer) {
        this.segmentDeserializer = segmentDeserializer;
    }

    public void setValidator(Validator validator) {
        this.validator = validator;
    }

}
