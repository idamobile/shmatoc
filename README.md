Shmatoc
=======

Tool like protoc but generates @Data classes with @Mapper annotations.

The output is strongly depends on:
* [protoc](https://code.google.com/p/protobuf/)
* [proto-mapper](https://github.com/idamobile/proto-mapper)
* [lombok](http://projectlombok.org)

Usage example
-------------

Let's say you have the following protobuf protocol `Sample.proto`:

        option java_outer_classname = "Sample";
        option java_package = "com.idamob.shmatoc.dto";
        option optimize_for = LITE_RUNTIME;
        
        message Entry {
            required string key = 1;
            optional string value = 2;
        }
        
        message Map {
            repeated Entry entries = 1;
        }
        
and you want this amazing data classes:
* `Entry.java`

        package com.output.sample;

        import com.idamob.shmatoc.dto.Sample;
        import com.shaubert.protomapper.annotations.Field;
        import com.shaubert.protomapper.annotations.Mapper;
        import lombok.Data;
        
        import java.io.Serializable;
        
        @Data
        @Mapper(protoClass = Sample.Entry.class)
        public class Entry implements Serializable {
            @Field private String key;
            @Field(optional = true) private String value;
        }

* and `Map.java`

        package com.output.sample;
        
        import com.idamob.shmatoc.dto.Sample;
        import com.shaubert.protomapper.annotations.Field;
        import com.shaubert.protomapper.annotations.Mapper;
        import lombok.Data;
        
        import java.io.Serializable;
        import java.util.ArrayList;
        import java.util.List;
        
        @Data
        @Mapper(protoClass = Sample.Map.class)
        public class Map implements Serializable {
            @Field private List<Entry> entries = new ArrayList<Entry>();
        }

All you need is to type `>java -jar shmatoc.jar --package_out com.output.sample --proto_files Sample.proto`
