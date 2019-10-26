import info.kinterest.DONTDOTHIS
import info.kinterest.datastore.Datastore
import info.kinterest.datastore.NOSTORE
import info.kinterest.entity.*
import info.kinterest.filter.filter
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterFunTest {
    class AEntity(override val id: Long, val name:String?, var age:Int) : KIEntity<Long> {
        override val _store: Datastore = NOSTORE
        override val _meta: KIEntityMeta = AEntity

        override fun <V> getValue(propertyName: PropertyName): V = when(propertyName.name) {
            "name" -> name as V
            "age" -> age as V
            else -> DONTDOTHIS()
        }

        override fun asTransient(): KITransientEntity<Long> {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun equals(other: Any?): Boolean = _equals(other)

        companion object : KIEntityMeta {
            override val type: KClass<*>
                get() = AEntity::class
            override val idType: KClass<*>
                get() = Long::class
            override val idGenerated: Boolean
                get() = true
            override val parentMeta: KIEntityMeta?
                get() = null
            override val baseMeta: KIEntityMeta
                get() = this
            override val properties: Map<PropertyName, PropertyMeta>
                get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

            override fun <ID : Any> instance(_store: Datastore, id: Any): KIEntity<ID> {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }
        }
    }

    @Test
    fun testFilter() {
        val f = filter<Long,AEntity>(AEntity) {
            1 lte "age" and (2 lte "age")
        }
        val e1 = AEntity(0, null, 1)
        val e2 = AEntity(1, null, 2)
        val e3 = AEntity(2, null, 3)

        assertFalse(f.matches(e1))
        assertTrue(f.matches(e3))
    }
}